package com.nogomy.net;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView webView;
    private RouterOsApi api;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showLogin();
    }

    private void showLogin() {

        String html =
                "<!DOCTYPE html>" +
                "<html lang='ar' dir='rtl'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>" +
                "body{margin:0;background:#070a18;color:#fff;font-family:Tahoma,Arial;padding:20px}" +
                ".box{max-width:450px;margin:40px auto;background:#11162a;padding:25px;border-radius:18px}" +
                "h1{color:#31d7a6;text-align:center}" +
                "input,button,select{width:100%;box-sizing:border-box;padding:14px;margin:7px 0;border-radius:10px;border:1px solid #303750;font-size:15px}" +
                "input,select{background:#080c19;color:#fff}" +
                "button{background:#31d7a6;color:#071018;font-weight:bold;border:0}" +
                ".status{text-align:center;color:#9aa2b4;font-size:13px;margin-top:12px}" +
                "</style></head>" +
                "<body>" +
                "<div class='box'>" +
                "<h1>النجومي نت</h1>" +
                "<p style='text-align:center'>إدارة MikroTik</p>" +
                "<input id='ip' placeholder='عنوان الراوتر' value='10.0.0.1'>" +
                "<input id='port' placeholder='منفذ API' value='8728'>" +
                "<input id='user' placeholder='اسم المستخدم' value='admin'>" +
                "<input id='pass' type='password' placeholder='كلمة المرور'>" +
                "<button onclick='connect()'>اتصال بالراوتر</button>" +
                "<div id='status' class='status'>أدخل بيانات الراوتر ثم اضغط اتصال</div>" +
                "</div>" +
                "<script>" +
                "function connect(){" +
                "let ip=document.getElementById('ip').value;" +
                "let port=document.getElementById('port').value;" +
                "let user=document.getElementById('user').value;" +
                "let pass=document.getElementById('pass').value;" +
                "document.getElementById('status').innerText='جاري الاتصال...';" +
                "Android.connect(ip,port,user,pass);" +
                "}" +
                "</script>" +
                "</body></html>";

        setupWebView();
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void setupWebView() {

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        setContentView(webView);
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void connect(
                String host,
                String port,
                String user,
                String pass) {

            executor.submit(() -> {

                try {

                    RouterOsApi newApi = new RouterOsApi();

                    newApi.connect(
                            host.trim(),
                            Integer.parseInt(port.trim()),
                            user,
                            pass
                    );

                    api = newApi;

                    runOnUiThread(() -> showDashboard());

                } catch (Exception e) {

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "document.getElementById('status').innerText=" +
                                    "'" + escapeJs("فشل الاتصال: " + e.getMessage()) + "';",
                                    null
                            )
                    );
                }
            });
        }

        @JavascriptInterface
        public void addCards(
                String profile,
                String validityValue,
                String validityUnit,
                String count,
                String usernameLength) {

            if (api == null) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "لا يوجد اتصال بالراوتر",
                                Toast.LENGTH_LONG
                        ).show()
                );
                return;
            }

            executor.submit(() -> {

                try {

                    int total = Integer.parseInt(count);
                    int length = Integer.parseInt(usernameLength);

                    String timeLimit;

                    if ("hour".equals(validityUnit)) {
                        timeLimit = validityValue + "h";
                    } else {
                        timeLimit = validityValue + "d";
                    }

                    String email;

                    if ("hour".equals(validityUnit)) {
                        email = validityValue + "h@nobind.com";
                    } else {
                        email = validityValue + "@nobind.com";
                    }

                    int success = 0;

                    for (int i = 0; i < total; i++) {

                        String username =
                                generateRandomString(length);

                        try {

                            api.addHotspotUser(
                                    username,
                                    username,
                                    profile,
                                    timeLimit,
                                    email
                            );

                            success++;

                        } catch (Exception ignored) {
                        }
                    }

                    final int result = success;

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "showAddResult(" + result + "," + total + ");",
                                    null
                            )
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "فشل توليد الكروت: " + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }
            });
        }

        @JavascriptInterface
        public void getUsers() {

            if (api == null) return;

            executor.submit(() -> {

                try {

                    List<Map<String, String>> users =
                            api.getHotspotUsers();

                    StringBuilder json =
                            new StringBuilder("[");

                    for (int i = 0; i < users.size(); i++) {

                        Map<String, String> m = users.get(i);

                        if (i > 0) json.append(",");

                        json.append("{")
                                .append("\"name\":\"")
                                .append(jsonEscape(
                                        m.getOrDefault("name", "-")
                                ))
                                .append("\",\"profile\":\"")
                                .append(jsonEscape(
                                        m.getOrDefault("profile", "-")
                                ))
                                .append("\",\"email\":\"")
                                .append(jsonEscape(
                                        m.getOrDefault("email", "-")
                                ))
                                .append("\"}");
                    }

                    json.append("]");

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "showUsers(" + json + ");",
                                    null
                            )
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "تعذر جلب المستخدمين",
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }
            });
        }

        @JavascriptInterface
        public void getActive() {

            if (api == null) return;

            executor.submit(() -> {

                try {

                    List<Map<String, String>> active =
                            api.getHotspotActive();

                    StringBuilder json =
                            new StringBuilder("[");

                    for (int i = 0; i < active.size(); i++) {

                        Map<String, String> m = active.get(i);

                        if (i > 0) json.append(",");

                        json.append("{")
                                .append("\"name\":\"")
                                .append(jsonEscape(
                                        m.getOrDefault("user", "-")
                                ))
                                .append("\",\"ip\":\"")
                                .append(jsonEscape(
                                        m.getOrDefault("address", "-")
                                ))
                                .append("\"}");
                    }

                    json.append("]");

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "showActive(" + json + ");",
                                    null
                            )
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "تعذر جلب المتصلين",
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }
            });
        }
    }

    private void showDashboard() {

        String html =
                "<!DOCTYPE html>" +
                "<html lang='ar' dir='rtl'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<script src='https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4'></script>" +
                "<style>" +
                "body{font-family:Tahoma,Arial;background:#070a18;color:white}" +
                "</style>" +
                "</head>" +
                "<body class='p-4'>" +

                "<header class='bg-slate-900 border-b border-slate-800 p-4 rounded-xl mb-5 flex justify-between'>" +
                "<b class='text-emerald-400'>🟢 النجومي نت - لوحة التحكم</b>" +
                "<button onclick='logout()' class='bg-red-500/20 text-red-400 px-3 py-2 rounded-lg'>خروج</button>" +
                "</header>" +

                "<div class='grid grid-cols-2 gap-3 mb-5'>" +

                "<button onclick='addPage()' class='bg-slate-900 p-5 rounded-2xl border border-purple-500/30'>" +
                "➕<br><b>إضافة كروت</b>" +
                "</button>" +

                "<button onclick='Android.getActive()' class='bg-slate-900 p-5 rounded-2xl border border-emerald-500/30'>" +
                "🟢<br><b>الكروت النشطة</b>" +
                "</button>" +

                "<button onclick='Android.getUsers()' class='bg-slate-900 p-5 rounded-2xl border border-sky-500/30'>" +
                "📋<br><b>جميع الكروت</b>" +
                "</button>" +

                "<button onclick='showUsed()' class='bg-slate-900 p-5 rounded-2xl border border-amber-500/30'>" +
                "🔒<br><b>المستخدمة</b>" +
                "</button>" +

                "</div>" +

                "<div id='content' class='bg-slate-900 rounded-2xl p-5'>" +
                "<h2 class='font-bold mb-3'>لوحة التحكم</h2>" +
                "<p class='text-slate-400 text-sm'>اختر القسم المطلوب.</p>" +
                "</div>" +

                "<script>" +

                "function addPage(){" +

                "document.getElementById('content').innerHTML=" +

                "`<h2 class='text-lg font-bold mb-4 text-purple-400'>إنشاء وإضافة كروت</h2>" +

                "<label>Profile</label>" +
                "<input id='profile' value='default' class='w-full p-3 bg-slate-950 rounded-lg mb-3'>" +

                "<label>عدد الكروت</label>" +
                "<input id='count' type='number' value='100' class='w-full p-3 bg-slate-950 rounded-lg mb-3'>" +

                "<label>عدد أرقام المستخدم</label>" +
                "<input id='length' type='number' value='8' class='w-full p-3 bg-slate-950 rounded-lg mb-3'>" +

                "<label>الصلاحية</label>" +
                "<div class='flex gap-2 mb-3'>" +
                "<input id='validity' type='number' value='1' class='w-1/2 p-3 bg-slate-950 rounded-lg'>" +
                "<select id='unit' class='w-1/2 p-3 bg-slate-950 rounded-lg'>" +
                "<option value='day'>يوم</option>" +
                "<option value='hour'>ساعة</option>" +
                "</select>" +
                "</div>" +

                "<div id='emailPreview' class='text-emerald-400 text-center mb-3'>البريد: 1@nobind.com</div>" +

                "<button onclick='generateCards()' class='w-full bg-purple-600 p-3 rounded-xl font-bold'>توليد وإضافة للمايكروتيك</button>" +

                "<div id='result' class='text-center mt-4'></div>`;" +

                "document.getElementById('validity').oninput=updateEmail;" +
                "document.getElementById('unit').onchange=updateEmail;" +
                "}" +

                "function updateEmail(){" +
                "let v=document.getElementById('validity').value||1;" +
                "let u=document.getElementById('unit').value;" +
                "document.getElementById('emailPreview').innerText='البريد: '+v+(u==='hour'?'h':'')+'@nobind.com';" +
                "}" +

                "function generateCards(){" +
                "Android.addCards(" +
                "document.getElementById('profile').value," +
                "document.getElementById('validity').value," +
                "document.getElementById('unit').value," +
                "document.getElementById('count').value," +
                "document.getElementById('length').value" +
                ");" +
                "document.getElementById('result').innerText='جاري إنشاء الكروت وإضافتها...';" +
                "}" +

                "function showAddResult(success,total){" +
                "document.getElementById('result').innerText='تمت إضافة '+success+' من '+total+' كرت إلى MikroTik';" +
                "}" +

                "function showUsers(data){" +
                "let h='<h2 class=\"text-lg font-bold mb-3\">جميع الكروت ('+data.length+')</h2><div>';" +
                "data.forEach(x=>{h+='<div class=\"border-b border-slate-700 p-3\">👤 '+x.name+'<br>Profile: '+x.profile+'<br>Email: '+x.email+'</div>';});" +
                "h+='</div>';" +
                "document.getElementById('content').innerHTML=h;" +
                "}" +

                "function showActive(data){" +
                "let h='<h2 class=\"text-lg font-bold mb-3 text-emerald-400\">المتصلون الآن ('+data.length+')</h2>';" +
                "data.forEach(x=>{h+='<div class=\"border-b border-slate-700 p-3\">🟢 '+x.name+'<br>IP: '+x.ip+'</div>';});" +
                "document.getElementById('content').innerHTML=h;" +
                "}" +

                "function showUsed(){" +
                "document.getElementById('content').innerHTML='<h2 class=\"text-lg font-bold\">الكروت المستخدمة</h2><p class=\"text-slate-400 mt-3\">سيتم ربط هذا القسم ببيانات الاستخدام من MikroTik.</p>';" +
                "}" +

                "function logout(){" +
                "Android.logout();" +
                "}" +

                "</script>" +
                "</body></html>";

        setupWebView();
        webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
        );
    }

    private String generateRandomString(int length) {

        String chars = "0123456789";

        StringBuilder result = new StringBuilder();

        Random random = new Random();

        for (int i = 0; i < length; i++) {
            result.append(
                    chars.charAt(
                            random.nextInt(chars.length())
                    )
            );
        }

        return result.toString();
    }

    private String jsonEscape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String escapeJs(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    protected void onDestroy() {

        if (api != null) {
            api.close();
        }

        executor.shutdownNow();

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
