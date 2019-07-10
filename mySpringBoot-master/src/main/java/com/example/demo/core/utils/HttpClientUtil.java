package com.example.demo.core.utils;

import com.alibaba.fastjson.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

public class HttpClientUtil {

    private static final int THREAD_COUNT = 10;
    private static CloseableHttpClient httpClient;
    private static ExecutorService executors;
    private static PoolingHttpClientConnectionManager cm;

    static {
        executors = Executors.newFixedThreadPool(THREAD_COUNT);
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plainsf)
                .register("https", sslsf)
                .build();
        cm = new PoolingHttpClientConnectionManager(registry);
        // 将最大连接数增加到200
        cm.setMaxTotal(200);
        // 将每个路由基础的连接增加到20
        cm.setDefaultMaxPerRoute(20);

        //请求重试处理
        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= 5) {// 如果已经重试了5次，就放弃
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {// 不要重试SSL握手异常
                    return false;
                }
                if (exception instanceof InterruptedIOException) {// 超时
                    return false;
                }
                if (exception instanceof UnknownHostException) {// 目标服务器不可达
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {// 连接被拒绝
                    return false;
                }
                if (exception instanceof SSLException) {// ssl握手异常
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                // 如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };

        httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setRetryHandler(httpRequestRetryHandler)
                .build();
    }
    public static void release() {
        if (executors != null) {
            executors.shutdown();
            executors = null;
        }
        if (cm != null) {
            cm.shutdown();
            cm = null;
        }
    }
    private static void config(HttpRequestBase httpRequestBase) {
        httpRequestBase.setHeader("User-Agent", "Mozilla/5.0");
        httpRequestBase.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        httpRequestBase.setHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");//"en-US,en;q=0.5");
        httpRequestBase.setHeader("Accept-Charset", "ISO-8859-1,utf-8,gbk,gb2312;q=0.7,*;q=0.7");

        // 配置请求的超时设置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(3000)
                .setConnectTimeout(3000)
                .setSocketTimeout(3000)
                .build();
        httpRequestBase.setConfig(requestConfig);
    }

    private static String executeMethod(HttpRequestBase httpget) {//, CountDownLatch countDownLatch
        CloseableHttpResponse response = null;
        String result = "";
        try {
            response = httpClient.execute(httpget, HttpClientContext.create());
            HttpEntity entity = response.getEntity();
            result = EntityUtils.toString(entity, "utf-8");
            EntityUtils.consume(entity);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (response != null)
                    response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static String get(String url) {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> param) {

        if (param != null && param.size() > 0) {
            String paramsStr = "?";

            try {
                for (String key : param.keySet()) {
                    paramsStr += key + "=" + URLEncoder.encode(param.get(key), "UTF-8") + "&";
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("字符编码不支持！");
            }
            paramsStr = StringUtils.substring(paramsStr, 0, paramsStr.length() - 1);
            url = url + paramsStr;
        }
        HttpGet request = new HttpGet(url);
        config(request);
//        List<NameValuePair> nvps = mapToPairList(param);
        String responeText = executeMethod(request);
        return responeText;

    }

    public static String post(String url) {
        return post(url, null);
    }

    public static String post(String url, Map<String, String> param){
        HttpPost request = new HttpPost(url);
        config(request);
        List<NameValuePair> nvps = mapToPairList(param);
        try {
            request.setEntity(new UrlEncodedFormEntity(nvps));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("字符编码不支持！");
        }
        String responeText = executeMethod(request);
        return responeText;
    }
    public static String postJson(String url,JSONObject obj){
        HttpPost request = new HttpPost(url);
        config(request);
        request.addHeader("Content-Type", "application/json;charset=UTF-8");
        // 解决中文乱码问题
        StringEntity stringEntity = new StringEntity(obj.toString(), "UTF-8");
        stringEntity.setContentEncoding("UTF-8");
        request.setEntity(stringEntity);
        String responeText = executeMethod(request);
        return responeText;
    }


    private static List<NameValuePair> mapToPairList(Map<String, String> paramsMap) {
        List<NameValuePair> postParameters = new ArrayList<>();
        if(paramsMap != null){
            for (String key : paramsMap.keySet()) {
                postParameters.add(new BasicNameValuePair(key, paramsMap.get(key)));
            }
        }
        return postParameters;
    }

    /**
	 * 简单的get请求
	 * GET 请求指定的页面信息，并返回实体主体
	 * @throws IOException
	 */
	public static String getDemo(String url) throws IOException {
	    //实例化httpclient，（4.5新版本和以前不同），实例化方式有两种
	    CloseableHttpClient httpClient = HttpClients.createDefault();
	    HttpGet httpGet = new HttpGet(url);
	    httpGet.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv:60.0) Gecko/20100101 Firefox/60.0");
	    CloseableHttpResponse response = null;
	    try {
	        /**
	         * 底层http链接仍由响应对象保存
	         * 允许直接从网络套接字流式传输响应内容
	         * 为了确保正确释放系统资源
	         * 用户必须从finally子句中调用CloseableHttpResponse #close()
	         */
	        response = httpClient.execute(httpGet);
	        System.out.println(response.getStatusLine());
	        HttpEntity entity = response.getEntity();
	        //对响应主体做一些有用的事情
	        //并确保他完全被消耗掉
	       return EntityUtils.toString(entity,"utf-8");
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        response.close();
	    }
	    return "";
	}
}