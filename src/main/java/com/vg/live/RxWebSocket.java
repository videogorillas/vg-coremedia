package com.vg.live;

import static com.squareup.okhttp.ws.WebSocket.BINARY;
import static com.squareup.okhttp.ws.WebSocket.TEXT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import rx.Emitter.BackpressureMode;
import rx.Observable;

public class RxWebSocket {
    private final static Logger log = LoggerFactory.getLogger(RxWebSocket.class);
    public static final String OPEN = "open";
    public static final String CLOSE = "close";
    public static final String MESSAGE = "message";
    public static final String PONG = "pong";

    public static class WebSocketEvent {
        public String type;
        public WebSocket ws;
        public Buffer payload;
        public Response response;
        public String message;
        public int code;
        public String reason;
    }

    static WebSocketEvent pong(WebSocket webSocket, Buffer payload) {
        WebSocketEvent e = new WebSocketEvent();
        e.type = PONG;
        e.ws = webSocket;
        e.payload = payload;
        return e;
    }

    static WebSocketEvent open(WebSocket webSocket, Response response) {
        WebSocketEvent e = new WebSocketEvent();
        e.type = OPEN;
        e.ws = webSocket;
        e.response = response;
        return e;
    }

    static WebSocketEvent msg(WebSocket webSocket, String message) {
        WebSocketEvent e = new WebSocketEvent();
        e.type = MESSAGE;
        e.ws = webSocket;
        e.message = message;
        return e;
    }

    static WebSocketEvent close(WebSocket webSocket, int code, String reason) {
        WebSocketEvent e = new WebSocketEvent();
        e.type = CLOSE;
        e.ws = webSocket;
        e.code = code;
        e.reason = reason;
        return e;
    }
    
    private Observable<WebSocketEvent> wsEvents;
    private Observable<WebSocket> openWebSockets;

    public RxWebSocket(Observable<WebSocketEvent> wsEvents) {
        this.wsEvents = wsEvents.doOnUnsubscribe(() -> {
            log.debug("unsubscribe wsEvents");
        }).share().doOnNext(e -> {
            log.debug("event: " + e.type + " " + e.message + " " + e.ws);
        });

        this.openWebSockets = this.wsEvents.filter(e -> e.type.equals(OPEN)).map(e -> e.ws).doOnNext(ws -> {
            log.debug("getOpenWebSockets open {}", ws);
        }).replay(1).refCount();
    }
    
    public Observable<WebSocket> getOpenWebSockets() {
        return openWebSockets;
    }
    
    public Observable<String> getMessages() {
        return wsEvents.filter(e -> e.type.equals(MESSAGE)).map(e -> e.message);
    }
    
    public Observable<String> sendText(String text, long timeoutMsec) {
        return sendText(getOpenWebSockets(), text, timeoutMsec);
    }
    
    public static RxWebSocket createRxWebSocket(OkHttpClient c, String url, long reconnectMsec) {
        return new RxWebSocket(reconnectWebSocket(c, url, reconnectMsec));
    }

    public static Observable<String> webSocketMessages(OkHttpClient c, Request request) {
        return webSocket(c, request).filter(e -> MESSAGE.equals(e.type)).map(e -> e.message);
    }

    public static Observable<WebSocketEvent> webSocket(OkHttpClient c, Request request) {
        return Observable.create(o -> {
            log.debug("webSocket {}", request);
            WebSocketCall call = WebSocketCall.create(c, request);
            WebSocket ws[] = new WebSocket[1];
            o.setCancellation(() -> {
                if (ws[0] != null) {
                    ws[0].close(1000, "canceled");
                }
                log.debug("cancel ws {}", request);
                call.cancel();
                log.debug("canceled ws {}", request);
            });


            call.enqueue(new WebSocketListener() {
                @Override
                public void onPong(Buffer payload) {
                    o.onNext(pong(ws[0], payload));
                }

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    ws[0] = webSocket;
                    o.onNext(open(webSocket, response));
                }

                @Override
                public void onMessage(ResponseBody message) throws IOException {
                    o.onNext(msg(ws[0], message.string()));
                }

                @Override
                public void onFailure(IOException e, Response response) {
                    o.onError(e);
                }

                @Override
                public void onClose(int code, String reason) {
                    o.onNext(close(ws[0], code, reason));
                    o.onCompleted();
                }
            });
        }, BackpressureMode.ERROR);
    }

    public static Observable<ByteBuffer> sendBuf(Observable<WebSocket> ws, ByteBuffer buf, long maxTimeMsec) {
        Observable<ByteBuffer> sendBuf = RxWebSocket.sendBuf(ws, buf);
        return sendBuf.retryWhen(errors -> errors.concatMap(err -> {
            int sz = buf.remaining();
            String id = Integer.toHexString(System.identityHashCode(buf)) + "/" + sz;
            log.error("retry send buf {} because {}", id, err.toString());
            if (err instanceof TimeoutException || err instanceof SocketTimeoutException) {
                return Observable.just("x");
            }
            return Observable.just("x").delay(maxTimeMsec, MILLISECONDS);
        }));
    }
    
    public static Observable<ByteBuffer> sendBuf(Observable<WebSocket> ws, ByteBuffer buf) {
        Observable<ByteBuffer> sent = ws.take(1).concatMap(_ws -> {
            int sz = buf.remaining();
            String id = Integer.toHexString(System.identityHashCode(buf)) + "/" + sz;
            log.debug("_ws {}", _ws);
            RequestBody message = RequestBody.create(BINARY, buf.array(), buf.arrayOffset(), sz);
            return Observable.fromCallable(() -> {
                log.debug("sending {}", id);
                try {
                    _ws.sendMessage(message);
                } catch (IOException e) {
                    log.error("error sendBuf {} {}", id, e.toString());
                    _ws.close(1000, "hz");
                }
                log.debug("sent {}", id);
                return buf;
            });
        });
        return sent;
    }
    
    public static Observable<String> sendText(Observable<WebSocket> ws, String buf, long maxTimeMsec) {
        Observable<String> sendBuf = RxWebSocket.sendText(ws, buf);
        sendBuf = sendBuf.timeout(maxTimeMsec, MILLISECONDS);
        return sendBuf.retryWhen(errors -> errors.concatMap(err -> {
            log.error("retry send text of size {} because {}", buf.length(), err.toString());
            if (err instanceof TimeoutException) {
                return Observable.just("x");
            }
            return Observable.just("x").delay(maxTimeMsec, MILLISECONDS);
        }));
    }
    
    public static Observable<String> sendText(Observable<WebSocket> ws, String text) {
        log.trace("sendText {}", text);
        Observable<String> sent = ws.take(1).concatMap(_ws -> {
            log.debug("_ws " + _ws);
            RequestBody message = RequestBody.create(TEXT, text);
            return Observable.fromCallable(() -> {
                log.debug("sending {} bytes", message.contentLength());
                try {
                    _ws.sendMessage(message);
                } catch (IOException e) {
                    log.error("error sendText {}", e.toString());
                    _ws.close(1000, "hz");
                }
                log.debug("sent {} bytes", message.contentLength());
                return text;
            });
        });
        return sent;
    }
    
    public static Observable<WebSocketEvent> reconnectWebSocket(OkHttpClient c, String url, long reconnectMsec) {
        return webSocket(c, GET(url))
                .repeatWhen(e -> e.doOnNext(err -> log.error("repeat {}", err.toString())).delay(reconnectMsec, MILLISECONDS))
                .retryWhen(e -> e.doOnNext(err -> log.error("retry {}", err.toString())).delay(reconnectMsec, MILLISECONDS));
    }
    
    public static Request GET(String url) {
        return new Request.Builder().url(url).build();
    }

}
