package com.dane.printerdemo;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import android.os.AsyncTask;
import okhttp3.*;
import java.io.IOException;

public class ApiService {

    private final OkHttpClient client = new OkHttpClient();

    public void fetchData(String url, String jsonBody, Callback callback) {
        new FetchDataTask(url, jsonBody, callback).execute();
    }

    private class FetchDataTask extends AsyncTask<Void, Void, String> {
        private final String url;
        private final String jsonBody;
        private final Callback callback;
        private Exception exception;

        FetchDataTask(String url, String jsonBody, Callback callback) {
            this.url = url;
            this.jsonBody = jsonBody;
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                return sendPostRequest(url, jsonBody);
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (exception != null) {
                callback.onError(exception);
            } else {
                callback.onSuccess(result);
            }
        }
    }

    private String sendPostRequest(String url, String json) throws IOException {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }

    public interface Callback {
        void onSuccess(String response);
        void onError(Exception e);
    }
}

