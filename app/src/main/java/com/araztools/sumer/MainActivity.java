package com.araztools.sumer;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_BT = 41;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final int[] matrix = new int[256];
    private final ArrayDeque<Integer> history = new ArrayDeque<>();
    // Single numeric input stream from the scanner controller.
    private int matrixCursor = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
    private long received = 0;
    private String currentTab = "خانه";
    private boolean matrixActive = false, demo = false;
    private BluetoothSocket socket;
    private Thread reader;
    private TextView status, valueText, minText, maxText, countText, serialLog;
    private EditText manualInput;
    private FrameLayout pageHost;
    private GraphView graph;
    private MatrixView matrixView;
    private LinearLayout root, nav;
    private final String[] tabs = {"خانه", "سریال", "عددی / Live", "خطی", "ماتریسی", "تحلیل"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(10,18,29));
        getWindow().setNavigationBarColor(Color.rgb(10,18,29));
        buildShell();
        showTab("خانه");
    }
    private int dp(float x){ return (int)(x*getResources().getDisplayMetrics().density+.5f); }
    private TextView text(String s,int size,int color){ TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setPadding(dp(6),dp(7),dp(6),dp(7));return t; }
    private LinearLayout column(){ LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(14),dp(10),dp(14),dp(14));return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}
    private void buildShell(){
        root=column();root.setBackgroundColor(Color.rgb(12,19,29));setContentView(root);
        TextView brand=text("SUMER  •  SMART SCANNER",20,Color.WHITE);root.addView(brand);
        status=text("● آماده | Bluetooth: قطع",12,Color.rgb(110,220,170));root.addView(status);
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
        nav=row();hsv.addView(nav);root.addView(hsv);
        pageHost=new FrameLayout(this);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);root.addView(pageHost,pp);
        for(String tab:tabs){Button b=new Button(this);b.setText(tab);b.setTextSize(11);b.setPadding(dp(5),0,dp(5),0);b.setOnClickListener(v->showTab(tab));nav.addView(b);}
    }
    private void showTab(String tab){
        currentTab=tab;graph=null;matrixView=null;pageHost.removeAllViews();LinearLayout p=column();ScrollView sc=new ScrollView(this);sc.addView(p);pageHost.addView(sc);
        p.addView(text(tab,22,Color.WHITE));
        if(tab.equals("خانه")) home(p);
        else if(tab.equals("سریال")) serial(p);
        else if(tab.equals("عددی / Live")) numeric(p);
        else if(tab.equals("خطی")) linear(p);
        else if(tab.equals("ماتریسی")) matrixMode(p);
        else if(tab.equals("تحلیل")) analysis(p);
    }
    private void stats(LinearLayout p){
        LinearLayout r=row();r.addView(stat("آخرین مقدار",valueText=text("—",18,Color.WHITE)));
        r.addView(stat("MIN",minText=text("—",18,Color.WHITE)));r.addView(stat("MAX",maxText=text("—",18,Color.WHITE)));
        p.addView(r);countText=text("تعداد داده: 0",12,Color.LTGRAY);p.addView(countText);
    }
    private LinearLayout stat(String title,TextView value){
        LinearLayout b=column();b.setGravity(Gravity.CENTER);b.setBackgroundColor(Color.rgb(29,42,58));b.addView(text(title,11,Color.LTGRAY));b.addView(value);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));b.setLayoutParams(lp);return b;
    }
    private void home(LinearLayout p){
        p.addView(text("پنل مرکزی دستگاه",17,Color.rgb(115,195,230)));
        p.addView(text("ATmega128  •  HC-05 Bluetooth Classic / SPP\nیک کانال ورودی عددی واحد",14,Color.WHITE));
        stats(p);
        p.addView(button("اتصال به دستگاه",()->showTab("سریال")));
        p.addView(button("شروع نمایش Live",()->showTab("عددی / Live")));
        p.addView(button("اسکن خطی",()->showTab("خطی")));
        p.addView(button("اسکن ماتریسی",()->showTab("ماتریسی")));
        p.addView(button("تحلیل سیگنال ورودی",()->showTab("تحلیل")));
        p.addView(text("پروتکل: هر مقدار عددی در یک خط مستقل (۰ تا ۵۰۰۰). نشانگر مستقل a برای شروع/پایان بلوک اسکن ماتریسی.",12,Color.LTGRAY));
    }
    private void serial(LinearLayout p){
        p.addView(text("دریافت سریال Bluetooth SPP",16,Color.WHITE));
        p.addView(button("انتخاب دستگاه جفت‌شده و اتصال",()->chooseDevice()));
        p.addView(button("قطع اتصال",()->disconnect()));
        p.addView(text("ورود دستی برای تست دریافت:",13,Color.LTGRAY));
        EditText e=new EditText(this);e.setSingleLine(true);e.setHint("عدد ۰ تا ۵۰۰۰ یا a");p.addView(e);
        p.addView(button("ارسال به پردازشگر",()->{String s=e.getText().toString().trim();if(s.equalsIgnoreCase("a"))acceptLine("a");else try{acceptValue(Integer.parseInt(s));}catch(Exception ex){toast("مقدار نامعتبر است");}}));
        serialLog=text("— هنوز داده‌ای دریافت نشده —",12,Color.rgb(180,205,220));serialLog.setGravity(Gravity.START);p.addView(serialLog);
        p.addView(text("نرخ UART پیش‌فرض پروژه: 9600؛ دریافت SPP در قالب خط متنی.",12,Color.LTGRAY));
    }
    private void numeric(LinearLayout p){
        p.addView(text("نمایش عددی و Live",15,Color.rgb(115,195,230)));stats(p);
        graph=new GraphView();p.addView(graph,new LinearLayout.LayoutParams(-1,dp(150)));
        p.addView(text("تصویر بلوک Live — با دریافت تک‌تک داده‌ها فوراً رفرش می‌شود",13,Color.WHITE));
        matrixView=new MatrixView();p.addView(matrixView,new LinearLayout.LayoutParams(-1,dp(270)));
        p.addView(text("نمودار فقط ۳۰۰ نمونه اخیر را نشان می‌دهد؛ نقشه رنگی نیز با هر داده تازه به‌روزرسانی می‌شود.",12,Color.LTGRAY));
        manualControls(p);
        p.addView(button("پاک‌سازی داده‌های نمایشی",()->resetData()));
    }
    private void linear(LinearLayout p){
        p.addView(text("اسکن خطی — مسیر و تغییرات مقدار",15,Color.rgb(115,195,230)));stats(p);
        graph=new GraphView();p.addView(graph,new LinearLayout.LayoutParams(-1,dp(230)));
        p.addView(text("هر مقدار جدید به نقطه بعدی مسیر تبدیل می‌شود؛ نمودار با ورود هر داده تازه رسم می‌شود.",12,Color.LTGRAY));
        manualControls(p);p.addView(button("شروع مسیر جدید",()->{history.clear();received=0;refreshStats();if(graph!=null)graph.invalidate();}));
    }
    private void matrixMode(LinearLayout p){
        p.addView(text("نقشه رنگی ماتریسی ۱۶ × ۱۶",15,Color.rgb(115,195,230)));stats(p);
        matrixView=new MatrixView();p.addView(matrixView,new LinearLayout.LayoutParams(-1,dp(310)));
        p.addView(text("نشانگر a شروع/پایان اسکن ماتریسی است. هر مقدار جدید فوراً در خانه بعدی قرار می‌گیرد و تصویر همان لحظه رفرش می‌شود.",12,Color.LTGRAY));
        manualControls(p);
        p.addView(button("شروع / پایان ماتریس (a)",()->acceptLine("a")));
        p.addView(button("پاک‌سازی ماتریس",()->{matrixCursor=0;Arrays.fill(matrix,0);if(matrixView!=null)matrixView.invalidate();}));
    }
    private void analysis(LinearLayout p){
        p.addView(text("تحلیل سیگنال — تنها ورودی عددی دستگاه",15,Color.rgb(115,195,230)));stats(p);
        p.addView(text("یک مسیر داده ورودی مشترک؛ نمایش MIN / MAX، مقدار لحظه‌ای، روند تغییرات و نقشه رنگی از همان جریان داده.",13,Color.WHITE));
        graph=new GraphView();p.addView(graph,new LinearLayout.LayoutParams(-1,dp(210)));
        manualControls(p);
        p.addView(button("بازنشانی تحلیل",()->resetData()));
    }
    private void manualControls(LinearLayout p){
        p.addView(text("آزمایش دستی مقدار عددی (۰ تا ۵۰۰۰)",14,Color.WHITE));
        EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(2);field.setHint("مثلاً 2450");p.addView(field);
        p.addView(button("ارسال مقدار",()->{try{int n=Integer.parseInt(field.getText().toString().trim());if(n<0||n>5000)throw new Exception();acceptValue(n);}catch(Exception ex){toast("فقط عدد صحیح بین ۰ تا ۵۰۰۰ وارد کنید");}}));
    }
    private void acceptLine(String line){
        if(line.trim().equalsIgnoreCase("a")){matrixActive=!matrixActive;appendLog(matrixActive?"a  → شروع اسکن ماتریسی":"a  → پایان اسکن ماتریسی");status.setText(matrixActive?"● اسکن ماتریسی فعال":"● اسکن ماتریسی پایان یافت");return;}
        try{acceptValue(Integer.parseInt(line.trim()));}catch(Exception ignored){appendLog("داده نامعتبر: "+line);}
    }
    private void acceptValue(int v){
        if(v<0||v>5000)return;
        received++;min=Math.min(min,v);max=Math.max(max,v);
        history.addLast(v);while(history.size()>300)history.removeFirst();
        matrix[matrixCursor++%256]=v;
        refreshStats();
        if(graph!=null)graph.invalidate();if(matrixView!=null)matrixView.invalidate();
        appendLog("RX  "+v);
    }
    private void refreshStats(){
        if(valueText!=null)valueText.setText(String.valueOf(history.isEmpty()?"—":history.peekLast()));
        if(minText!=null)minText.setText(min==Integer.MAX_VALUE?"—":String.valueOf(min));
        if(maxText!=null)maxText.setText(max==Integer.MIN_VALUE?"—":String.valueOf(max));
        if(countText!=null)countText.setText("تعداد داده: "+received);
    }
    private void resetData(){history.clear();Arrays.fill(matrix,0);matrixCursor=0;received=0;min=Integer.MAX_VALUE;max=Integer.MIN_VALUE;refreshStats();if(graph!=null)graph.invalidate();if(matrixView!=null)matrixView.invalidate();}
    private int colorFor(int v){if(max==Integer.MIN_VALUE||min==Integer.MAX_VALUE||max==min)return Color.rgb(35,135,205);float f=(v-min)/(float)(max-min);return Color.HSVToColor(new float[]{240f-240f*Math.max(0,Math.min(1,f)),.95f,.98f});}
    private void appendLog(String s){if(serialLog!=null){String old=serialLog.getText().toString();serialLog.setText((s+"\n"+old).substring(0,Math.min(3500,(s+"\n"+old).length())));}}
    private void chooseDevice(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},REQ_BT);return;}
        try{
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){toast("Bluetooth در این گوشی موجود نیست");return;}
            Set<BluetoothDevice> paired=a.getBondedDevices();if(paired.isEmpty()){toast("ابتدا HC-05 را از تنظیمات گوشی Pair کنید");return;}
            ArrayList<BluetoothDevice> list=new ArrayList<>(paired);String[] labels=new String[list.size()];for(int i=0;i<list.size();i++)labels[i]=list.get(i).getName()+"  •  "+list.get(i).getAddress();
            new AlertDialog.Builder(this).setTitle("دستگاه Bluetooth").setItems(labels,(d,w)->connect(list.get(w))).setNegativeButton("انصراف",null).show();
        }catch(SecurityException e){toast("مجوز Bluetooth داده نشده است");}
    }
    private void connect(BluetoothDevice device){
        disconnect();status.setText("● در حال اتصال...");
        reader=new Thread(()->{try{
            socket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket.connect();
            InputStream in=socket.getInputStream();ui.post(()->status.setText("● متصل به "+device.getName()));
            ByteArrayOutputStream line=new ByteArrayOutputStream();int b;
            while(!Thread.currentThread().isInterrupted()&&(b=in.read())!=-1){if(b=='\n'||b=='\r'){if(line.size()>0){String s=line.toString("UTF-8").trim();line.reset();ui.post(()->acceptLine(s));}}else if(line.size()<128)line.write(b);}
        }catch(Exception e){ui.post(()->status.setText("● اتصال قطع شد: "+e.getMessage()));}finally{try{if(socket!=null)socket.close();}catch(Exception ignored){}}});
        reader.start();
    }
    private void disconnect(){try{if(reader!=null)reader.interrupt();if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;reader=null;if(status!=null)status.setText("● Bluetooth: قطع");}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==REQ_BT&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)chooseDevice();else toast("برای اتصال Bluetooth مجوز لازم است");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private class GraphView extends View{
        Paint paint=new Paint(3);GraphView(){super(MainActivity.this);setBackgroundColor(Color.rgb(20,31,46));}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();paint.setColor(Color.rgb(65,82,103));paint.setStrokeWidth(1);for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,paint);if(history.size()<2)return;paint.setColor(Color.rgb(35,190,230));paint.setStrokeWidth(dp(2));float x=0,prevY=0;int i=0;for(int v:history){float y=h-(v/5000f)*h;if(i++>0)c.drawLine(x,prevY,(i-1)*w/Math.max(1,history.size()-1),y,paint);prevY=y;x=(i-1)*w/Math.max(1,history.size()-1);}}
    }
    private class MatrixView extends View{
        Paint paint=new Paint(3);MatrixView(){super(MainActivity.this);setBackgroundColor(Color.rgb(17,27,41));}
        protected void onDraw(Canvas c){super.onDraw(c);float cell=Math.min(getWidth(),getHeight())/16f;float left=(getWidth()-cell*16)/2,top=(getHeight()-cell*16)/2;for(int i=0;i<256;i++){paint.setColor(matrix[i]==0?Color.rgb(28,42,58):colorFor(matrix[i]));c.drawRect(left+(i%16)*cell,top+(i/16)*cell,left+(i%16+1)*cell-1,top+(i/16+1)*cell-1,paint);}}
    }
    @Override protected void onDestroy(){disconnect();super.onDestroy();}
}