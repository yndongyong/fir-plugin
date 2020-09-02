package io.jenkins.plugins.sample;

public class ApiTokenResponse {

    private CertBean cert;

    public CertBean getCert() {
        return cert;
    }

    public void setCert(CertBean cert) {
        this.cert = cert;
    }

    @Override
    public String toString() {
        return "ApiTokenResponse{" +
                "cert=" + cert +
                '}';
    }

    public static class CertBean {
        private DataBean icon;
        private DataBean binary;


        public DataBean getIcon() {
            return icon;
        }

        public void setIcon(DataBean icon) {
            this.icon = icon;
        }

        public DataBean getBinary() {
            return binary;
        }

        public void setBinary(DataBean binary) {
            this.binary = binary;
        }

        @Override
        public String toString() {
            return "CertBean{" +
                    "icon=" + icon +
                    ", binary=" + binary +
                    '}';
        }
    }

    public static class DataBean {

        private String key;
        private String token;
        private String upload_url;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUpload_url() {
            return upload_url;
        }

        public void setUpload_url(String upload_url) {
            this.upload_url = upload_url;
        }

        @Override
        public String toString() {
            return "DataBean{" +
                    "key='" + key + '\'' +
                    ", token='" + token + '\'' +
                    ", upload_url='" + upload_url + '\'' +
                    '}';
        }
    }
}
