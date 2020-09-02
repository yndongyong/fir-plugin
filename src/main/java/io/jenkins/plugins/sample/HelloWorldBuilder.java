package io.jenkins.plugins.sample;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HelloWorldBuilder extends Recorder {

    public static final String LOG_PREFIX = "[UPLOAD TO FIR] - ";
    public static final String ICON_PREFIX = "/root/.jenkins/workspace/$JOB_NAME/app/src/main/";


    private final String apiToken;
    private final String scanDir;
    private final String wildcard;
    private final String updateDescription;
    private final String firUrl;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @DataBoundConstructor
    public HelloWorldBuilder(String apiToken,String scanDir,String wildcard,String updateDescription,String firUrl) {
        this.apiToken = apiToken;
        this.scanDir = scanDir;
        this.wildcard = wildcard;
        this.updateDescription = updateDescription;
        this.firUrl = firUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getScanDir() {
        return scanDir;
    }

    public String getWildcard() {
        return wildcard;
    }

    public String getUpdateDescription() {
        return updateDescription;
    }

    public String getFirUrl() {
        return firUrl;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //判断编译是成功还是失败
        if (build.getResult() == Result.FAILURE){
            printMessage(listener, true, "build failure can not execute upload task");
            return false;
        }

        //print header
        listener.getLogger().println("");
        listener.getLogger().println( "**************************************************************************************************");
        listener.getLogger().println( "**************************************************************************************************");
        listener.getLogger().println( "********************************          UPLOAD TO FIR BY yndongyong          **************************");
        listener.getLogger().println( "********************************         FIR:https://www.betaqr.com     **************************");
        listener.getLogger().println( "**************************************************************************************************");
        listener.getLogger().println( "**************************************************************************************************");
        listener.getLogger().println("");


        String dir = build.getEnvironment(listener).expand(scanDir);
        String apkFilePath = build.getEnvironment(listener).expand(wildcard);
        String changeLogVar = build.getEnvironment(listener).expand(updateDescription);

        //find file
        String uploadFilePath = findFile(dir, apkFilePath, listener);


        if (StringUtils.isEmpty(uploadFilePath)){
            printMessage(listener, true, "The uploaded file was not found，plase check scandir or wildcard!\n");
            return false;
        }


        File uploadFile = new File(uploadFilePath);
        if (!uploadFile.exists() || !uploadFile.isFile()) {
            printMessage(listener, true, "The uploaded file was not found，plase check scandir or wildcard!\n");
            return false;
        }

        ApkFile apkFile = new ApkFile(new File(uploadFilePath));
        ApkMeta apkMeta = apkFile.getApkMeta();

        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .retryOnConnectionFailure(true)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build();

        //获取token
        printMessage(listener, true, "start get access token " +" to " + firUrl);
        Map<String, String> param = Maps.newHashMap();
        param.put("api_token", this.apiToken);
        param.put("bundle_id", apkMeta.getPackageName());
        param.put("type", "android");
        //type 另外两个参数，首次发布应用才需要，所以这里暂时不考虑
        RequestBody apiTokenBody =
                RequestBody.create(JSON, new Gson().toJson(param));

        Request apiTokenRequest =new Request.Builder()
                .url(firUrl)
                .post(apiTokenBody)
                .addHeader("Content-Type", "application/json")
                .build();

        Response apiTokenResponse = okHttpClient.newCall(apiTokenRequest).execute();
        if (apiTokenResponse==null || apiTokenResponse.body() == null){
            printMessage(listener, true, "Upload failed with fir api : " + firUrl);
            return false;
        }

        String apiTokenResult = apiTokenResponse.body().string();

        ApiTokenResponse apiTokenData = null;
        try {
            apiTokenData = new Gson().fromJson(apiTokenResult, new TypeToken<ApiTokenResponse>() {
            }.getType());
        } catch (Exception e) {
            e.printStackTrace();
            printMessage(listener, true, e.getMessage());
            return false;
        }

        if (apiTokenData.getCert() == null ||  apiTokenData.getCert().getBinary() == null || apiTokenData.getCert().getBinary().getKey() == null){
            printMessage(listener, true, "Upload failed with fir get api token : " + firUrl);
            return false;
        }


        //upload icon
        if (apiTokenData.getCert().getIcon() != null) {
            ApiTokenResponse.DataBean iconData = apiTokenData.getCert().getIcon();
            String iconPath = build.getEnvironment(listener).expand(ICON_PREFIX) + apkMeta.getIcon();
            if (iconPath.contains("mipmap-mdpi-v4")){
                iconPath = iconPath.replace("mipmap-mdpi-v4", "mipmap-xxhdpi");
            }
            printMessage(listener, true, "The uploaded icon path : "+ iconPath);
            uploadIcon(okHttpClient,iconData.getKey(),iconData.getToken(),iconData.getUpload_url(),iconPath,listener);
        }

        //upload  file
        ApiTokenResponse.DataBean binary = apiTokenData.getCert().getBinary();
        MediaType type = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(type, uploadFile);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MediaType.parse("multipart/form-data"))
                .addFormDataPart("key",binary.getKey())
                .addFormDataPart("token", binary.getToken())
                .addFormDataPart("file", uploadFile.getName(), fileBody)
                .addFormDataPart("x:name", apkMeta.getLabel())
                .addFormDataPart("x:version", apkMeta.getVersionName())
                .addFormDataPart("x:build", apkMeta.getVersionCode()+"")
                .addFormDataPart("x:changelog", changeLogVar)
                .build();

        Request uploadRequest = new Request.Builder()
                .url(binary.getUpload_url())
                .post(new ProgressRequestBody(requestBody, new FileUploadProgressListener(listener)))
                .build();


        Response uploadResponse = okHttpClient.newCall(uploadRequest).execute();
        if(uploadResponse == null || uploadResponse.body() == null){
            printMessage(listener, true, "Upload failed error : ");
            return  false;
        }
        String uploadResult = uploadResponse.body().string();
        printMessage(listener, true, "Upload file result: " + uploadResult);
        if (!StringUtils.isEmpty(uploadResult) && uploadResult.contains("\"is_completed\":true")){
            printMessage(listener, true, "Upload file success ");
            printMessage(listener, true, "应用名称：" + apkMeta.getLabel());
            printMessage(listener, true, "应用版本版本号：" + apkMeta.getVersionName());
            printMessage(listener, true, "应用版本版本code：" + apkMeta.getVersionCode());
            return true;
        }else{
            printMessage(listener, true, "Upload file failed ");
            return false;
        }
    }

    private void uploadIcon(OkHttpClient okHttpClient,String key,String token,String uploadUrl,String iconPath,TaskListener listener){

        File uploadFile = new File(iconPath);
        if (!uploadFile.exists() || !uploadFile.isFile()) {
            printMessage(listener, true, "The uploaded icon was not found!\n");
            return ;
        }
        MediaType type = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(type, uploadFile);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MediaType.parse("multipart/form-data"))
                .addFormDataPart("key",key)
                .addFormDataPart("token",token)
                .addFormDataPart("file", uploadFile.getName(), fileBody)
                .build();

        Request uploadRequest = new Request.Builder()
                .url(uploadUrl)
                .post(new ProgressRequestBody(requestBody, new FileUploadProgressListener(listener)))
                .build();


        Response uploadResponse = null;
        try {
            uploadResponse = okHttpClient.newCall(uploadRequest).execute();
            String uploadResult = uploadResponse.body().string();
            if (!StringUtils.isEmpty(uploadResult) && uploadResult.contains("\"is_completed\":true")) {
                printMessage(listener, true, "Upload icon success ");

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        if(uploadResponse == null || uploadResponse.body() == null){
            printMessage(listener, true, "Upload icon failed error");
        }


    }




    /**
      * print message
      *
      * @param listener listener
      * @param needTag  needTag
      * @param message  message
      */
    public static void printMessage(TaskListener listener, boolean needTag, String message) {
        if (listener != null) {
            listener.getLogger().println(LOG_PREFIX + message);
        }
    }


    /**
     * find file
     *
     * @param scandir  scandir
     * @param wildcard wildcard
     * @param listener listener
     * @return file path
     */
    public String findFile(String scandir, String wildcard, TaskListener listener) {
        File dir = new File(scandir);
        if (!dir.exists() || !dir.isDirectory()) {
            printMessage(listener, true, "scan dir:" + dir.getAbsolutePath());
            printMessage(listener, true, "scan dir isn't exist or it's not a directory!");
            return null;
        }

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(scandir);
        scanner.setIncludes(new String[]{wildcard});
        scanner.setCaseSensitive(true);
        scanner.scan();
        String[] uploadFiles = scanner.getIncludedFiles();

        if (uploadFiles == null || uploadFiles.length == 0) {
            return null;
        }
        if (uploadFiles.length == 1) {
            return new File(dir, uploadFiles[0]).getAbsolutePath();
        }

        List<String> strings = Arrays.asList(uploadFiles);
        Collections.sort(strings, new FileComparator(dir));
        String uploadFiltPath = new File(dir, strings.get(0)).getAbsolutePath();
        printMessage(listener, true, "Found " + uploadFiles.length + " files, the default choice of the latest modified file!");
        printMessage(listener, true, "The latest modified file is " + uploadFiltPath + "\n");
        return uploadFiltPath;
    }


    public static class FileComparator implements Comparator<String>, Serializable {
        File dir;

        public FileComparator(File dir) {
            this.dir = dir;
        }

        @Override
        public int compare(String o1, String o2) {
            File file1 = new File(dir, o1);
            File file2 = new File(dir, o2);
            return Long.compare(file2.lastModified(), file1.lastModified());
        }
    }


    public static class FileUploadProgressListener implements ProgressRequestBody.Listener {
        private long last_time = -1L;
        private final TaskListener listener;

        public FileUploadProgressListener(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public void onRequestProgress(long bytesWritten, long contentLength) {
            final int progress = (int) (100F * bytesWritten / contentLength);
            if (progress == 100) {
                last_time = -1L;
                printMessage(listener,true, "upload progress: " + progress + " %");
                return;
            }

            if (last_time == -1) {
                last_time = System.currentTimeMillis();
                printMessage(listener, true, "upload progress: " + progress + " %");
                return;
            }

            if (System.currentTimeMillis() - last_time > 1000) {
                last_time = System.currentTimeMillis();
                printMessage(listener, true, "upload progress: " + progress + " %");
            }
        }
    }


    @Symbol("yyn-upload-to-fir")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckApiToken(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a fir api_token");
            return FormValidation.ok();
        }

        public FormValidation doCheckScanDir(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set upload apk file base dir name");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckWildcard(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set upload ipa or apk file wildcard");
            }
            return FormValidation.ok();
        }


        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "YYN UPLOAD TO FIR";
        }

    }
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
