package com.koenv.jsonapi.docgenerator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.io.Files;
import com.koenv.jsonapi.docgenerator.generator.*;
import com.koenv.jsonapi.docgenerator.model.*;
import com.koenv.jsonapi.docgenerator.resolvers.ClassResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @Parameter
    private List<File> files = new ArrayList<>();

    @Parameter(names = {"-extra", "-e"})
    private String extraDirectoryPath = "docs";

    @Parameter(names = {"-templates", "-t"})
    private String templateDirectoryPath = extraDirectoryPath + "/templates";

    @Parameter(names = {"-output", "-o"})
    private String outputDirectoryPath = "output";

    @Parameter(names = {"-mappings", "-m"})
    private String mappingsPath = extraDirectoryPath + "/mappings.conf";

    public static void main(String[] args) {
        Main main = new Main();
        new JCommander(main, args);
        main.run();
    }

    private void run() {
        boolean valid = true;

        if (files.size() < 1) {
            logger.error("Please specify at least 1 API file");
            valid = false;
        }

        for (File file : files) {
            if (!file.exists() || !file.isFile()) {
                logger.error("File " + file.getPath() + " doesn't exist or isn't a file.");
                valid = false;
            }
        }

        File extraDirectory = new File(extraDirectoryPath);
        if (!extraDirectory.exists() || !extraDirectory.isDirectory()) {
            logger.error("Extra directory " + extraDirectory.getPath() + " doesn't exist or isn't a directory.");
            valid = false;
        }

        File templateDirectory = new File(templateDirectoryPath);
        if (!templateDirectory.exists() || !templateDirectory.isDirectory()) {
            logger.error("Template directory " + templateDirectory.getPath() + " doesn't exist or isn't a directory");
            valid = false;
        }

        File outputDirectory = new File(outputDirectoryPath);
        if (!outputDirectory.exists() || !outputDirectory.isDirectory()) {
            if (!outputDirectory.mkdirs()) {
                logger.error("Failed to create output directory " + outputDirectory.getPath());
                valid = false;
            }
        }

        File mappingsFile = new File(mappingsPath);
        if (mappingsFile.exists() && !mappingsFile.isFile()) {
            logger.error(mappingsFile.getPath() + " must be a file if it exists.");
            valid = false;
        }

        if (!valid) {
            return;
        }

        List<PlatformMethods> methods = new ArrayList<>();
        for (File file : files) {
            APIFileParser parser = new APIFileParser(file);
            try {
                methods.add(parser.parse());
            } catch (Exception e) {
                logger.error("Failed to parse file " + file.getPath(), e);
                return;
            }
        }

        if (methods.size() == 2) {
            PlatformMethods platform1 = methods.get(0);
            PlatformMethods platform2 = methods.get(1);
            MethodDiffResult<NamespacedMethod> namespacedMethodDiffResult = MethodDiffResult.diff(
                    platform1.getPlatform(),
                    platform1.getNamespacedMethods(),
                    platform2.getPlatform(),
                    platform2.getNamespacedMethods()
            );

            MethodDiffResult<ClassMethod> classMethodDiffResult = MethodDiffResult.diff(
                    platform1.getPlatform(),
                    platform1.getClassMethods(),
                    platform2.getPlatform(),
                    platform2.getClassMethods()
            );

            List<AbstractMethod> onlyInPlatform1 = new ArrayList<>(namespacedMethodDiffResult.getOnlyInPlatform1());
            onlyInPlatform1.addAll(classMethodDiffResult.getOnlyInPlatform1());

            List<AbstractMethod> onlyInPlatform2 = new ArrayList<>(namespacedMethodDiffResult.getOnlyInPlatform2());
            onlyInPlatform2.addAll(classMethodDiffResult.getOnlyInPlatform2());

            printDiffWarnings(platform1.getPlatform(), onlyInPlatform1);
            printDiffWarnings(platform2.getPlatform(), onlyInPlatform2);

            printStreamDiffWarnings(platform1.getPlatform(), platform1.getStreams(), platform2.getPlatform(), platform2.getStreams());
        } else {
            logger.warn("Not yet possible to gather information about possible method diffs");
        }

        HashSet<NamespacedMethod> allNamespacedMethods = new HashSet<>();
        HashSet<ClassMethod> allClassMethods = new HashSet<>();
        HashSet<String> streams = new HashSet<>();

        methods.forEach(platformMethods -> {
            allNamespacedMethods.addAll(platformMethods.getNamespacedMethods());
            allClassMethods.addAll(platformMethods.getClassMethods());
            streams.addAll(platformMethods.getStreams());
        });

        File classesDirectory = new File(extraDirectory, "classes");

        ClassResolver classResolver = new ClassResolver();
        allClassMethods.stream().collect(Collectors.groupingBy(ClassMethod::getOperatesOn)).keySet().forEach(s -> classResolver.register(new JSONAPIClass(s, true)));

        List<String> extraClasses = readClasses(classesDirectory, classResolver);

        if (mappingsFile.exists()) {
            Map<String, String> mappings = MappingsReader.readMappings(mappingsFile);
            mappings.forEach((key, value) -> {
                if (!classResolver.contains(value)) {
                    throw new IllegalArgumentException("Invalid mapping: Mapping " + value + " doesn't exist");
                }
                classResolver.register(key, classResolver.resolve(value));
            });
        }

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_24);
        try {
            configuration.setDirectoryForTemplateLoading(templateDirectory);
        } catch (IOException e) {
            logger.error("Failed to load template files", e);
        }
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false); // will already be thrown

        IndexGenerator indexGenerator = new IndexGenerator(
                outputDirectory,
                new ArrayList<>(allNamespacedMethods.stream().collect(Collectors.groupingBy(NamespacedMethod::getNamespace)).keySet()),
                new ArrayList<>(allClassMethods.stream().collect(Collectors.groupingBy(ClassMethod::getOperatesOn)).keySet()),
                new ArrayList<>(streams)
        );

        try (FileWriter fileWriter = new FileWriter(new File(outputDirectory, "index.html"))) {
            indexGenerator.generate(configuration, classResolver, fileWriter);
        } catch (IOException | TemplateException e) {
            logger.error("Failed to generate index file", e);
        }

        File namespaceDirectory = new File(extraDirectory, "namespaces");

        File namespaceOutputDirectory = new File(outputDirectory, "namespaces");
        namespaceOutputDirectory.mkdirs();

        allNamespacedMethods.stream().collect(Collectors.groupingBy(NamespacedMethod::getNamespace)).entrySet().forEach(entry -> {
            NamespaceDocGenerator generator = new NamespaceDocGenerator(namespaceDirectory, entry.getKey(), entry.getValue());

            File file = new File(namespaceOutputDirectory, entry.getKey() + ".html");

            try (FileWriter fileWriter = new FileWriter(file)) {
                generator.generate(configuration, classResolver, fileWriter);
            } catch (IOException | TemplateException e) {
                logger.error("Failed to generate documentation for namespace " + entry.getKey(), e);
            }
        });

        File classesOutputDirectory = new File(outputDirectory, "classes");
        classesOutputDirectory.mkdirs();

        allClassMethods.stream().collect(Collectors.groupingBy(ClassMethod::getOperatesOn)).entrySet().forEach(entry -> {
            ClassDocWithMethodsGenerator generator = new ClassDocWithMethodsGenerator(classesDirectory, entry.getKey(), entry.getValue());

            File file = new File(classesOutputDirectory, entry.getKey() + ".html");

            extraClasses.remove(entry.getKey()); // be extra sure to not generate twice

            try (FileWriter fileWriter = new FileWriter(file)) {
                generator.generate(configuration, classResolver, fileWriter);
            } catch (IOException | TemplateException e) {
                logger.error("Failed to generate documentation for class " + entry.getKey(), e);
            }
        });

        extraClasses.forEach(s -> {
            ClassDocGenerator generator = new ClassDocGenerator(classesDirectory, s);

            File file = new File(classesOutputDirectory, s + ".html");

            try (FileWriter fileWriter = new FileWriter(file)) {
                generator.generate(configuration, classResolver, fileWriter);
            } catch (IOException | TemplateException e) {
                logger.error("Failed to generate documentation for class " + s, e);
            }
        });

        File streamsDirectory = new File(extraDirectory, "streams");

        File streamsOutputDirectory = new File(outputDirectory, "streams");
        streamsOutputDirectory.mkdirs();

        streams.stream().forEach(stream -> {
            StreamDocGenerator generator = new StreamDocGenerator(streamsDirectory, stream);

            File file = new File(streamsOutputDirectory, stream + ".html");

            try (FileWriter fileWriter = new FileWriter(file)) {
                generator.generate(configuration, classResolver, fileWriter);
            } catch (IOException | TemplateException e) {
                logger.error("Failed to generate documentation for stream " + stream, e);
            }
        });
    }

    private <T extends AbstractMethod> void printDiffWarnings(Platform platform, List<T> methods) {
        if (methods.size() > 0) {
            logger.warn(methods.size() + " methods only available in " + platform.getName() + ":");
            for (AbstractMethod method : methods) {
                logger.warn("* " + method.getDeclaration());
            }
        }
    }

    private void printStreamDiffWarnings(Platform platform1, List<String> platform1Streams, Platform platform2, List<String> platform2Streams) {
        Set<String> onlyInPlatform1 = new HashSet<>(platform1Streams);
        onlyInPlatform1.removeAll(platform2Streams);

        Set<String> onlyInPlatform2 = new HashSet<>(platform2Streams);
        onlyInPlatform2.removeAll(platform1Streams);

        printStreamDiffWarnings(platform1, onlyInPlatform1);
        printStreamDiffWarnings(platform2, onlyInPlatform2);
    }

    private void printStreamDiffWarnings(Platform platform, Set<String> streams) {
        if (streams.size() > 0) {
            logger.warn(streams.size() + " streams only available in " + platform.getName() + ":");
            for (String stream : streams) {
                logger.warn("* " + stream);
            }
        }
    }

    private List<String> readClasses(File classesDirectory, ClassResolver resolver) {
        List<String> classes = new ArrayList<>();
        Arrays.asList(classesDirectory.listFiles()).forEach(file -> {
            if (file.isFile()) {
                String name = Files.getNameWithoutExtension(file.getPath());
                if (!resolver.contains(name)) {
                    resolver.register(new JSONAPIClass(name, true));
                    classes.add(name);
                }
            }
        });
        return classes;
    }
}