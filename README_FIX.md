这是修复后的APK文件，包含以下修复：

1. 在 AndroidManifest.xml 中注册了 WebViewLoginActivity
2. 现在点击"浏览器登录"按钮应该能正常打开B站登录页面了

注意：这个APK使用系统的WebView组件，B站可能会检测到非标准浏览器环境而要求验证码。如果遇到验证码，可能需要手动输入Cookie方式登录。