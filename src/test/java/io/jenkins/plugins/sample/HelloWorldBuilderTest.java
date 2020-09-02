package io.jenkins.plugins.sample;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
public class HelloWorldBuilderTest {

    @Test
    public void test01() throws IOException {
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .retryOnConnectionFailure(true)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build();




        Map<String, String> param = Maps.newHashMap();
        param.put("api_token", "6d3679d794ce3917e094839a1dacc8e1");
        param.put("bundle_id", "com.tengyun.yyn");
        param.put("type", "android");
        //type 另外两个参数，首次发布应用才需要，所以这里暂时不考虑
        RequestBody apiTokenBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), new Gson().toJson(param));

        Request apiTokenRequest =new Request.Builder()
                .url("http://api.bq04.com/apps")
                .post(apiTokenBody)
                .addHeader("Content-Type", "application/json")
                .build();

        Response apiTokenResponse = okHttpClient.newCall(apiTokenRequest).execute();
        if (apiTokenResponse==null || apiTokenResponse.body() == null){

        }

        String apiTokenResult = apiTokenResponse.body().string();
        System.out.println(apiTokenResult);

        ApiTokenResponse apiTokenData = null;
        try {
            apiTokenData = new Gson().fromJson(apiTokenResult, new TypeToken<ApiTokenResponse>() {
            }.getType());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(apiTokenData.toString());


        ApiTokenResponse.DataBean binary = apiTokenData.getCert().getBinary();
        System.out.println(binary);
    }


}