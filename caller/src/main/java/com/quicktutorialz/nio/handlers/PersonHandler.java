package com.quicktutorialz.nio.handlers;

import com.quicktutorialz.nio.models.Person;
import com.quicktutorialz.nio.utils.HttpUtils;
import com.quicktutorialz.nio.utils.JsonConverter;
import io.reactivex.Observable;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class PersonHandler  implements HttpHandler {

    JsonConverter json = JsonConverter.getInstance();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        /* move the request to a worker thread */
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        /* FIRST ALTERNATIVE:
        you can call the business logic directly because the whole body of handleRequest() is managed reactively
         */
        //String requestBody = getRequestBody(exchange);
        //Person person = (Person) json.getObjectFromJson(requestBody, Person.class);
        //Person p = transform(person);
        //sendResponse(exchange, json.getJsonOf(p));


        /* SECOND ALTERNATIVE: not working
        you must wrap business logic within a reactive construction (RxJava, CompletableFuture, ecc.) in order to
        have all the stack reactive
         */
        /*
        String requestBody = getRequestBody(exchange);
        CompletableFuture
                .supplyAsync(()-> (Person) json.getObjectFromJson(requestBody, Person.class))
                .thenApply(p -> transform(p))
                .thenAccept(p -> sendResponse(exchange, json.getJsonOf(p)));*/

        /* RxJava works fine */
        /*String requestBody = getRequestBody(exchange);
        Observable.fromCallable(()->(Person) json.getObjectFromJson(requestBody, Person.class))
                .map(p -> transform(p))
                .subscribe(t -> sendResponse(exchange, json.getJsonOf(t)));*/

        /* RxJava another more reactive version */
        Observable.fromCallable(()->(Person) getJsonResponseBody(exchange, Person.class))
                .map(p -> transform(p))
                .subscribe(w   -> sendResponse(exchange, json.getJsonOf(w)),
                           thr -> exchange.getResponseSender().send(thr.toString())
                );


    }

    private Object getJsonResponseBody(HttpServerExchange exchange, Class clazz){
        try {
            String requestBody = getRequestBody(exchange);
            return json.getObjectFromJson(requestBody, clazz);
        }catch(IOException e){
            exchange.getResponseSender().send(e.toString());
        }
        return null;
    }

    /* it extract the body of the request */
    private String getRequestBody(HttpServerExchange exchange) throws IOException {
        Pooled<ByteBuffer> pooledByteBuffer = exchange.getConnection().getBufferPool().allocate();
        ByteBuffer byteBuffer = pooledByteBuffer.getResource();
        byteBuffer.clear();
        exchange.getRequestChannel().read(byteBuffer);
        int pos = byteBuffer.position();
        byteBuffer.rewind();
        byte[] bytes = new byte[pos];
        byteBuffer.get(bytes);
        byteBuffer.clear();
        pooledByteBuffer.free();

        return new String(bytes, Charset.forName("UTF-8") );
    }

    /* it could be also a database fetch or whatever */
    private Person transform(Person p) throws IOException {
        if(p!=null){
            String host = System.getenv("CALLEDADDRESS");
            if("".equals(host) || host == null){
                host= "http://localhost:8081";
            }
            String address = host + "/called";
            String bodyResponse = new HttpUtils().post(address, json.getJsonOf(p));
            Person p1 = (Person) json.getObjectFromJson(bodyResponse, Person.class);
            return p1;
        }
        return p;
    }

    private void sendResponse(HttpServerExchange exchange, String response){
        exchange.getResponseHeaders()
                .put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender()
                .send(response);
    }




}