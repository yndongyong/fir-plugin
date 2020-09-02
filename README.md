前段时间由于fir.im网站的域名变更为`https://www.betaqr.com/`以及上传应用api变更，导致jenkins中上传到fir的插件失效了，官方也一直没有维护类似的插件。后用蒲公英平台替换fir平台，无奈最近蒲公英平台也访不了了，经过一段时间的不方便后还是回归到了fir平台，但是找不到可用的插件，只找到了`fir-cli`脚本插件，但是自己没有ci服务器的访问配置权限，还需要找去运维开权限，配置跳板机等，一顿操作下来费时费力，还需要更改目前的打包脚本。权衡后决定参照旧版本插件结合fir官方的api文档自己写出一个jenkins插件。本插件是直接在jenkins的插件系统中安装，然后在job的config中添加配置构建后步骤，配置本插件就可以使用。


### 插件的功能 ###
  
  只能够上传apk包到fir.im，不支持ipa包的上传（在插件开发过程中图方便没有再写支持ios包解析的逻辑）；支持上传icon、更新内容等。
  
### 插件的工作原理 ###
 - 1.通过插件配置参数找到构建包的生产路径，找到最新的一个构建包
 - 2.插件内部使用apk-paraser解析apk包，获取名称，包名、版本号、logo路径等。
 - 3.按照fir.im官方的api，使用okhttp获取key，upload_url然后上传logo，上传apk包，api包含的可选的参数都提交了。
  

### 插件配置的几个重要的参数 ###

- fir api_token：为fir.im 网站的api_token
- scandir : 最终构建包的文件目录
- file wildcard ： 构建包的后缀名，android填写：*.apk
- updateDescription ：更新日志（可选），支持jenkins的变量，或者自己注入的环境变量。
- fir_url：fir获取上传凭证的接口,已经设置默认值`http://api.bq04.com/apps`，预留的口，方便以后api地址又变更。

### 使用方法 ###
在jenkins的job的config中，添加构建后步骤，添加本插件，按插件要求配置参数即可，插件会扫描构建目录中最新的一个构建包上传到fir中。

插件下载链接：https://pan.baidu.com/s/1QYjUD5TNO9y2chuHw8MRqw 
提取码：zrpw