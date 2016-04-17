package com.koenv.jsonapi.streams;

import com.koenv.jsonapi.ErrorCodes;
import com.koenv.jsonapi.JSONAPI;
import com.koenv.jsonapi.http.model.APIException;
import com.koenv.jsonapi.http.model.JsonRequest;
import com.koenv.jsonapi.http.websocket.WebSocketInvoker;
import com.koenv.jsonapi.http.websocket.WebSocketStreamSubscriber;
import com.koenv.jsonapi.methods.APIMethod;
import com.koenv.jsonapi.methods.APINamespace;
import com.koenv.jsonapi.methods.Invoker;

import java.util.List;

@APINamespace("streams")
public class StreamMethods {
    @APIMethod
    public static boolean subscribe(Invoker invoker, JsonRequest request, String stream) {
        if (!(invoker instanceof WebSocketInvoker)) {
            throw new APIException("Subscriptions only work while connected to a web socket", ErrorCodes.INVALID_STREAM_USAGE);
        }

        WebSocketInvoker webSocketInvoker = (WebSocketInvoker) invoker;

        if (!webSocketInvoker.getUser().canAccessStream(stream)) {
            throw new APIException("No access to stream " + stream, ErrorCodes.ACCESS_DENIED);
        }

        StreamSubscriber streamSubscriber = new WebSocketStreamSubscriber(webSocketInvoker.getSession());

        try {
            JSONAPI.getInstance().getStreamManager().subscribe(stream, streamSubscriber, request.getTag());
        } catch (InvalidStreamException e) {
            throw new APIException("Invalid stream: " + stream, ErrorCodes.INVALID_STREAM);
        } catch (DuplicateSubscriptionException e) {
            final String[] tag = {null};
            JSONAPI.getInstance().getStreamManager().findSubscription(stream, streamSubscriber).findFirst().ifPresent(subscription -> tag[0] = subscription.getTag());
            throw new APIException("Duplicate stream for: " + stream + " with tag " + tag[0], ErrorCodes.DUPLICATE_SUBSCRIPTION);
        }

        return true;
    }

    @APIMethod
    public static int unsubscribe(Invoker invoker, JsonRequest request, String stream) {
        if (!(invoker instanceof WebSocketInvoker)) {
            throw new APIException("Subscriptions only work while connected to a web socket", ErrorCodes.INVALID_STREAM_USAGE);
        }

        WebSocketInvoker webSocketInvoker = (WebSocketInvoker) invoker;

        if (!webSocketInvoker.getUser().canAccessStream(stream)) {
            throw new APIException("No access to stream " + stream, ErrorCodes.ACCESS_DENIED);
        }

        StreamSubscriber streamSubscriber = new WebSocketStreamSubscriber(webSocketInvoker.getSession());

        return JSONAPI.getInstance().getStreamManager().unsubscribe(stream, streamSubscriber, request.getTag());
    }

    @APIMethod
    public static List<String> listStreams() {
        return JSONAPI.getInstance().getStreamManager().getStreams();
    }

    @APIMethod
    public static int subscriptionCount() {
        return JSONAPI.getInstance().getStreamManager().getSubscriptionCount();
    }
}
