package com.nogomy.net;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    LinearLayout root, content;
    EditText ip, port, user, pass;
    TextView status;
    RouterOsApi api;
    ExecutorService executor=Executors.newSingleThreadExecutor();

    int bg=Color.rgb(7,10,24), card=Color.rgb(17,22,42), text=Color.rgb(238,241,250), muted=Color.rgb(150,158,180), accent=Color.rgb(49,215,166);

    @Override public void onCreate(Bundle b){super.onCreate(b); showLogin();}

    TextView tv(String s,int sp){TextView t=new TextView(this); t.setText(s);t.setTextColor(text);t.setTextSize(sp);t.setPadding(4,8,4,8);return t;}
    EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(text);e.setHintTextColor(muted);e.setSingleLine(true);e.setBackgroundResource(com.nogomy.net.R.drawable.edit_bg);e.setPadding(16,0,16,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,58);p.setMargins(0,7,0,7);e.setLayoutParams(p);return e;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackgroundResource(R.drawable.button_bg);b.setPadding(8,0,8,0);return b;}

    void base(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,20,20,16);root.setBackgroundColor(bg);
        ScrollView sv=new ScrollView(this);sv.addView(root);setContentView(sv);
    }
    void showLogin(){
        base();
        TextView title=tv("النجومي نت",30);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(accent);root.addView(title);
        TextView sub=tv("إدارة MikroTik المحلية",19);sub.setTextColor(text);root.addView(sub);
        ip=field("عنوان الراوتر","10.0.0.1");root.addView(ip);
        port=field("منفذ API","8728");root.addView(port);
        user=field("اسم المستخدم","admin");root.addView(user);
        pass=field("كلمة المرور","");pass.setInputType(0x81);root.addView(pass);
        Button connect=btn("اتصال بالراوتر");root.addView(connect,new LinearLayout.LayoutParams(-1,58));
        status=tv("متصل محلياً؟ أدخل بيانات الراوتر ثم اضغط اتصال.",14);status.setTextColor(muted);root.addView(status);
        connect.setOnClickListener(v->doConnect());
    }
    void doConnect(){
        status.setText("جاري الاتصال...");
        executor.submit(()->{
            try{
                RouterOsApi a=new RouterOsApi();
                a.connect(ip.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()),user.getText().toString(),pass.getText().toString());
                api=a; runOnUiThread(()->showDashboard());
            }catch(Exception e){runOnUiThread(()->status.setText("فشل الاتصال: "+e.getMessage()));}
        });
    }
    void showDashboard(){
        base();
        TextView title=tv("النجومي نت",29);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(accent);root.addView(title);
        status=tv("● متصل بـ "+ip.getText().toString(),14);status.setTextColor(accent);root.addView(status);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        loadStats();
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button users=btn("المستخدمون");Button active=btn("المتصلون الآن");
        row.addView(users,new LinearLayout.LayoutParams(0,58,1));row.addView(active,new LinearLayout.LayoutParams(0,58,1));root.addView(row);
        users.setOnClickListener(v->loadUsers()); active.setOnClickListener(v->loadActive());
        Button add=btn("＋ إضافة مستخدم Hotspot");root.addView(add,new LinearLayout.LayoutParams(-1,58));add.setOnClickListener(v->showAdd());
        Button back=btn("تغيير الراوتر");root.addView(back,new LinearLayout.LayoutParams(-1,58));back.setOnClickListener(v->{api.close();showLogin();});
    }
    TextView cardText(String s){
        TextView t=tv(s,16);t.setBackgroundColor(card);t.setPadding(18,18,18,18);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,0);t.setLayoutParams(p);return t;
    }
    void loadStats(){
        executor.submit(()->{try{Map<String,String> r=api.getResource();runOnUiThread(()->{
            content.removeAllViews();
            content.addView(cardText("الموديل: "+r.getOrDefault("board-name","-")));
            content.addView(cardText("RouterOS: "+r.getOrDefault("version","-")));
            content.addView(cardText("المعالج: "+r.getOrDefault("cpu-load","-")+"%"));
            content.addView(cardText("الذاكرة الكلية: "+r.getOrDefault("total-memory","-")));
            content.addView(cardText("مدة التشغيل: "+r.getOrDefault("uptime","-")));
        });}catch(Exception e){runOnUiThread(()->toast("تعذر قراءة معلومات الراوتر"));}});
    }
    void loadUsers(){executor.submit(()->{try{List<Map<String,String>> u=api.getHotspotUsers();runOnUiThread(()->showList("مستخدمو Hotspot",u));}catch(Exception e){runOnUiThread(()->toast("تعذر جلب المستخدمين"));}});}
    void loadActive(){executor.submit(()->{try{List<Map<String,String>> u=api.getHotspotActive();runOnUiThread(()->showList("المتصلون الآن",u));}catch(Exception e){runOnUiThread(()->toast("تعذر جلب المتصلين"));}});}
    void showList(String title,List<Map<String,String>> list){
        content.removeAllViews();content.addView(tv(title+" ("+list.size()+")",21));
        if(list.isEmpty()){content.addView(cardText("لا توجد بيانات."));return;}
        for(Map<String,String> m:list){
            String name=m.getOrDefault("name",m.getOrDefault("user","-"));
            String extra="IP: "+m.getOrDefault("address","-")+"   Profile: "+m.getOrDefault("profile","-");
            content.addView(cardText("👤 "+name+"\n"+extra));
        }
    }
    void showAdd(){
        final EditText n=field("اسم المستخدم",""); final EditText p=field("كلمة المرور",""); final EditText prof=field("Profile","default"); final EditText limit=field("Limit uptime (مثال 1d)","1d");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(22,5,22,5);
        box.addView(n);box.addView(p);box.addView(prof);box.addView(limit);
        new AlertDialog.Builder(this).setTitle("إضافة Hotspot User").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("إضافة",(d,w)->executor.submit(()->{
            try{api.addHotspotUser(n.getText().toString(),p.getText().toString(),prof.getText().toString(),limit.getText().toString());runOnUiThread(()->toast("تمت إضافة المستخدم"));}catch(Exception e){runOnUiThread(()->toast("فشل الإضافة: "+e.getMessage()));}
        })).show();
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(api!=null)api.close();executor.shutdownNow();super.onDestroy();}
}
