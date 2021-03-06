package com.koenv.universalminecraftapi.parser.expressions;

/**
 * A string expression, such as `"test"`.
 */
public class StringExpression extends ValueExpression {
    private String value;

    public StringExpression(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringExpression that = (StringExpression) o;

        return value != null ? value.equals(that.value) : that.value == null;

    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StringExpression{" +
                "value='" + value + '\'' +
                '}';
    }
}
