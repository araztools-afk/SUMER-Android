package com.araztools.sumer;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ArrayDeque<Integer> samples=new ArrayDeque<>();
  private final ArrayList<String> logs=new ArrayList<>();
  private final int[] palette={0xff18bfff,0xff7c5cff,0xff20c997,0xffffb547,0xffef5b78};
  private int accent=0xff18bfff, last=-1,min=5001,max=-1,baud=9600;
  private boolean dark=true,feedbackSound=false,feedbackVibe=true,alertSound=false,alertVibe=false,autoRunning=false;
  private String tab="HOME",alertMode="MANUAL";
  private TextView status,brand;
  private LinearLayout root,content,bottom;
  private BluetoothSocket socket;
  private Thread reader;
  private static final int BT_REQ=71;
  private int bg(){return dark?0xff080f1b:0xffeef3f9;}
  private int panel(){return dark?0xff111f32:0xffffffff;}
  private int panel2(){return dark?0xff172941:0xffe2eaf4;}
  private int fg(){return dark?0xfff2f7ff:0xff142238;}
  private int muted(){return dark?0xff9aacc2:0xff62748b;}
  private int line(){return dark?0xff263c57:0xffd2ddea;}
  private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setNavigationBarColor(bg());getWindow().setStatusBarColor(bg());getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);if(!getPreferences(0).getBoolean("activated",false)){registration();}else shell();}
  private TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private void pad(View v,int a){v.setPadding(dp(a),dp(a),dp(a),dp(a));}
  private void registration(){
    LinearLayout page=col();page.setGravity(Gravity.CENTER);page.setPadding(dp(28),dp(24),dp(28),dp(24));page.setBackgroundColor(bg());setContentView(page);
    TextView logo=txt("SUMER",42,fg());logo.setGravity(Gravity.CENTER);logo.setTypeface(null,1);page.addView(logo);
    TextView sub=txt("MAGNETOMETRIC SURVEY SYSTEM",12,accent);sub.setGravity(Gravity.CENTER);sub.setLetterSpacing(.13f);page.addView(sub);
    TextView shield=txt("◈",68,accent);shield.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(125));sp.topMargin=dp(28);page.addView(shield,sp);
    TextView title=txt("SERIAL REGISTRATION",19,fg());title.setGravity(Gravity.CENTER);title.setTypeface(null,1);page.addView(title);
    TextView hint=txt("Enter your personal activation code to unlock SUMER.",14,muted());hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(10),0,dp(22));page.addView(hint);
    EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(fg());input.setHintTextColor(muted());input.setHint("SUMER-XXXX-XXXX-XXXX");input.setBackground(shape(panel(),line()));pad(input,14);page.addView(input,new LinearLayout.LayoutParams(-1,dp(58)));
    TextView note=txt("Prototype registration: format validation only. Production license verification requires the license service.",11,muted());note.setPadding(dp(4),dp(12),dp(4),dp(12));page.addView(note);
    Button activate=btn("ACTIVATE  →");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(54));ap.topMargin=dp(8);page.addView(activate,ap);
    TextView error=txt("",13,0xffff647c);error.setGravity(Gravity.CENTER);page.addView(error);
    activate.setOnClickListener(v->{tap();String s=input.getText().toString().trim().toUpperCase(Locale.US);if(s.matches("SUMER-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")){getPreferences(0).edit().putBoolean("activated",true).putString("license",s).apply();shell();}else error.setText("Invalid serial format. Please check the code.");});
  }
  private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(0xffffffff);b.setAllCaps(false);b.setTextSize(14);b.setBackground(shape(accent,0));return b;}
  private TextView label(String s){TextView t=txt(s,12,muted());t.setPadding(dp(2),dp(10),dp(2),dp(5));return t;}
  private void shell(){
    getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root=col();root.setBackgroundColor(bg());setContentView(root);
    LinearLayout top=row();top.setPadding(dp(16),dp(9),dp(16),dp(9));
    brand=txt("SUMER",25,fg());brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
    TextView gear=txt("⚙",25,accent);gear.setGravity(Gravity.CENTER);gear.setOnClickListener(v->{tap();show("SETTINGS");});top.addView(gear,new LinearLayout.LayoutParams(dp(46),dp(44)));root.addView(top);
    status=txt("●  DEVICE DISCONNECTED     •     9600 BAUD",11,muted());status.setPadding(dp(16),dp(10),dp(16),dp(10));status.setBackground(shape(panel(),line()));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(42));sl.setMargins(dp(14),0,dp(14),dp(10));root.addView(status,sl);status.setOnClickListener(v->{tap();show("DEVICE");});
    ScrollView scroll=new ScrollView(this);content=col();content.setPadding(dp(14),dp(4),dp(14),dp(18));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    bottom=row();bottom.setPadding(dp(8),dp(7),dp(8),dp(7));bottom.setBackgroundColor(panel());root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(60)));
    show(tab);
  }
  private void show(String name){
    tab=name;content.removeAllViews();bottom.removeAllViews();
    if(name.equals("HOME"))home();else if(name.equals("LIVE"))live();else if(name.equals("PIN"))pin();else if(name.equals("AUTOMATIC"))automatic();else if(name.equals("MANUAL"))manual();else if(name.equals("SETTINGS"))settings();else if(name.equals("DEVICE"))device();else if(name.equals("TERMINAL"))terminal();
    String[] names={"HOME","LIVE","PIN","AUTOMATIC","MANUAL","SETTINGS"};
    String[] icons={"⌂","◉","⌖","▷","✥","⚙"};
    for(int i=0;i<names.length;i++){final String n=names[i];TextView item=txt(icons[i]+"\n"+names[i],9,n.equals(tab)?accent:muted());item.setGravity(Gravity.CENTER);item.setOnClickListener(v->{tap();show(n);});bottom.addView(item,new LinearLayout.LayoutParams(0,-1,1));}
  }
  private void card(View v){v.setBackground(shape(panel(),line()));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(v,p);}
  private TextView heading(String s){TextView t=txt(s,18,fg());t.setTypeface(null,1);t.setPadding(dp(2),dp(14),dp(2),dp(12));return t;}
  private void home(){
    content.addView(heading("MAGNETOMETRIC SURVEY"));
    TextView sub=txt("Single-channel acquisition workspace",12,muted());content.addView(sub);
    LinearLayout dev=row();TextView dt=txt("⌁   DEVICE CONNECTION",14,fg());dt.setTypeface(null,1);dev.addView(dt,new LinearLayout.LayoutParams(0,dp(54),1));TextView go=txt("CONNECT  ›",11,accent);dev.addView(go);dev.setPadding(dp(14),0,dp(14),0);dev.setBackground(shape(panel(),line()));dev.setOnClickListener(v->{tap();show("DEVICE");});LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(54));dlp.topMargin=dp(18);dlp.bottomMargin=dp(18);content.addView(dev,dlp);
    LinearLayout r1=row();r1.addView(tile("◉","LIVE","Real-time view","LIVE"),new LinearLayout.LayoutParams(0,dp(154),1));space(r1);r1.addView(tile("⌖","PIN","Point survey","PIN"),new LinearLayout.LayoutParams(0,dp(154),1));content.addView(r1);
    LinearLayout r2=row();r2.addView(tile("▷","AUTOMATIC","Automatic scan","AUTOMATIC"),new LinearLayout.LayoutParams(0,dp(154),1));space(r2);r2.addView(tile("✥","MANUAL","Manual survey","MANUAL"),new LinearLayout.LayoutParams(0,dp(154),1));content.addView(r2);
    LinearLayout set=row();TextView st=txt("⚙   SETTINGS",14,fg());st.setTypeface(null,1);set.addView(st);set.setPadding(dp(16),0,dp(16),0);set.setBackground(shape(panel(),line()));set.setOnClickListener(v->{tap();show("SETTINGS");});LinearLayout.LayoutParams setp=new LinearLayout.LayoutParams(-1,dp(48));setp.topMargin=dp(18);content.addView(set,setp);
    TextView term=txt("OPEN DATA TERMINAL  →",11,accent);term.setGravity(Gravity.CENTER);term.setPadding(0,dp(16),0,dp(8));term.setOnClickListener(v->{tap();show("TERMINAL");});content.addView(term);
  }
  private void space(LinearLayout r){View s=new View(this);r.addView(s,new LinearLayout.LayoutParams(dp(10),1));}
  private View tile(String icon,String title,String desc,String dest){
    LinearLayout box=col();box.setGravity(Gravity.CENTER);box.setPadding(dp(10),dp(12),dp(10),dp(12));box.setBackground(shape(panel(),line()));
    TextView i=txt(icon,36,accent);i.setGravity(Gravity.CENTER);box.addView(i,new LinearLayout.LayoutParams(-1,dp(58)));
    TextView t=txt(title,15,fg());t.setGravity(Gravity.CENTER);t.setTypeface(null,1);box.addView(t);
    TextView d=txt(desc,10,muted());d.setGravity(Gravity.CENTER);box.addView(d);
    box.setOnClickListener(v->{tap();show(dest);});return box;
  }
  private void stats(){
    LinearLayout r=row();String[] heads={"CURRENT","MIN","MAX","SAMPLES"};String[] vals={last<0?"—":""+last,min>5000?"—":""+min,max<0?"—":""+max,""+samples.size()};
    for(int i=0;i<4;i++){LinearLayout c=col();c.setGravity(Gravity.CENTER);c.setPadding(dp(5),dp(10),dp(5),dp(10));c.setBackground(shape(panel(),line()));TextView h=txt(heads[i],9,muted());h.setGravity(Gravity.CENTER);c.addView(h);TextView v=txt(vals[i],18,i==0?accent:fg());v.setGravity(Gravity.CENTER);v.setTypeface(null,1);c.addView(v);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(66),1);cp.setMargins(dp(2),0,dp(2),0);r.addView(c,cp);}content.addView(r);
  }
  private void live(){
    content.addView(heading("LIVE MONITOR"));content.addView(txt("One live block • previous frame is replaced",12,muted()));stats();
    TextView block=txt(last<0?"WAITING FOR DATA":""+last,36,0xffffffff);block.setGravity(Gravity.CENTER);block.setTypeface(null,1);block.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff164bdb,0xff00b7d7,0xff00b76c,0xffffc629,0xffef3b32}));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(190));bp.topMargin=dp(14);bp.bottomMargin=dp(12);content.addView(block,bp);
    content.addView(heading("SIGNAL TREND"));card(new Graph());
    Button pause=btn("CLEAR LIVE MIN / MAX");pause.setOnClickListener(v->{tap();min=5001;max=-1;show("LIVE");});content.addView(pause);
  }
  private class Graph extends View{Paint p=new Paint(3);Graph(){super(MainActivity.this);setBackground(shape(panel(),line()));}protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(190));}protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(line());p.setStrokeWidth(1);for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,p);if(samples.size()<2)return;p.setColor(accent);p.setStrokeWidth(dp(2));float x0=0,y0=0;int i=0,n=samples.size();for(int v:samples){float x=(float)i++/(n-1)*w,y=h-(v/5000f)*h;if(i>1)c.drawLine(x0,y0,x,y,p);x0=x;y0=y;}}}
  private void manual(){
    content.addView(heading("MANUAL SURVEY"));content.addView(txt("Enter a single measurement or use the connected stream.",12,muted()));stats();
    EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(2);field.setHint("Value 0–5000");field.setTextColor(fg());field.setHintTextColor(muted());field.setBackground(shape(panel(),line()));pad(field,14);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(58));fp.topMargin=dp(18);content.addView(field,fp);
    Button add=btn("ADD MEASUREMENT");add.setOnClickListener(v->{tap();try{int n=Integer.parseInt(field.getText().toString());accept(n);field.setText("");show("MANUAL");}catch(Exception e){toast("Enter an integer from 0 to 5000");}});content.addView(add);
    content.addView(heading("RECENT VALUES"));for(Integer n:samples){TextView t=txt("●     "+n,14,fg());t.setPadding(dp(12),dp(10),dp(12),dp(10));card(t);}
  }
  private void automatic(){
    content.addView(heading("AUTOMATIC SURVEY"));content.addView(txt("Automatic acquisition view. Start and stop the incoming stream display.",12,muted()));stats();
    TextView state=txt(autoRunning?"●  ACQUISITION ACTIVE":"○  READY",17,autoRunning?0xff20c997:muted());state.setGravity(Gravity.CENTER);state.setBackground(shape(panel(),line()));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(88));p.topMargin=dp(20);content.addView(state,p);
    Button b=btn(autoRunning?"STOP SESSION":"START SESSION");b.setOnClickListener(v->{tap();autoRunning=!autoRunning;show("AUTOMATIC");});content.addView(b);
    content.addView(txt("This mode does not generate synthetic measurements; it displays actual incoming values only.",11,muted()));
  }
  private void pin(){
    content.addView(heading("PIN SURVEY"));content.addView(txt("Capture a point measurement and label its location.",12,muted()));stats();
    EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Point name / ID");name.setTextColor(fg());name.setBackground(shape(panel(),line()));pad(name,12);content.addView(name);
    Button b=btn("SAVE CURRENT VALUE AS POINT");b.setOnClickListener(v->{tap();if(last<0){toast("No measurement received yet");return;}String s=name.getText().toString().trim();if(s.isEmpty())s="Point "+(logs.size()+1);logs.add(0,s+"  •  "+last);toast("Point saved in this session");});content.addView(b);
    content.addView(heading("SESSION POINTS"));for(String s:logs){TextView t=txt("⌖   "+s,14,fg());t.setPadding(dp(12),dp(10),dp(12),dp(10));card(t);}
  }
  private void settings(){
    content.addView(heading("SETTINGS"));content.addView(label("APPEARANCE"));
    addChoice("Theme",new String[]{"Dark","Light"},dark?0:1,i->{dark=i==0;shell();});
    addChoice("Accent colour",new String[]{"Ice blue","Violet","Emerald","Amber","Rose"},paletteIndex(),i->{accent=palette[i];shell();});
    content.addView(label("TOUCH FEEDBACK"));
    addChoice("Feedback",new String[]{"Sound","Vibration","Both","None"},feedbackSound&&feedbackVibe?2:feedbackSound?0:feedbackVibe?1:3,i->{feedbackSound=i==0||i==2;feedbackVibe=i==1||i==2;});
    content.addView(label("SIGNAL ALERTS  •  MANUAL"));
    addChoice("Alert output",new String[]{"Speaker","Vibration","Both","None"},alertSound&&alertVibe?2:alertSound?0:alertVibe?1:3,i->{alertSound=i==0||i==2;alertVibe=i==1||i==2;});
    content.addView(label("CONNECTION"));
    addChoice("Baud rate",new String[]{"1200","2400","4800","9600","19200","38400","57600","115200"},new int[]{1200,2400,4800,9600,19200,38400,57600,115200}.length>0?baudIndex():3,i->{int[] a={1200,2400,4800,9600,19200,38400,57600,115200};baud=a[i];updateStatus();});
    TextView about=txt("SUMER • MAGNETOMETRIC SURVEY SYSTEM\nSingle-channel prototype • Android",12,muted());about.setPadding(dp(4),dp(20),dp(4),dp(20));content.addView(about);
  }
  private int paletteIndex(){for(int i=0;i<palette.length;i++)if(palette[i]==accent)return i;return 0;}
  private int baudIndex(){int[] a={1200,2400,4800,9600,19200,38400,57600,115200};for(int i=0;i<a.length;i++)if(a[i]==baud)return i;return 3;}
  interface Pick{void choose(int i);}
  private void addChoice(String title,String[] opts,int selected,Pick pick){
    LinearLayout box=col();box.setPadding(dp(13),dp(8),dp(13),dp(8));box.setBackground(shape(panel(),line()));TextView t=txt(title,13,fg());box.addView(t);Spinner sp=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);sp.setAdapter(a);sp.setSelection(Math.max(0,selected));sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){}public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){pick.choose(pos);}});box.addView(sp);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);content.addView(box,p);
  }
  private void device(){
    content.addView(heading("DEVICE CONNECTION"));content.addView(txt("Bluetooth Classic / SPP • paired devices",12,muted()));
    Button c=btn("SELECT PAIRED BLUETOOTH DEVICE");c.setOnClickListener(v->{tap();chooseDevice();});content.addView(c);
    Button d=btn("DISCONNECT");d.setOnClickListener(v->{tap();disconnect();});content.addView(d);
    addChoice("Baud rate",new String[]{"1200","2400","4800","9600","19200","38400","57600","115200"},baudIndex(),i->{int[] a={1200,2400,4800,9600,19200,38400,57600,115200};baud=a[i];updateStatus();});
    content.addView(txt("Pair the scanner in Android Bluetooth settings first. The baud selection is a project setting and must match the transmitter configuration.",11,muted()));
  }
  private void terminal(){
    content.addView(heading("DATA TERMINAL"));content.addView(txt("Raw incoming values • one number per line",12,muted()));
    for(String s:logs){TextView t=txt(s,13,0xff20c997);t.setPadding(dp(10),dp(7),dp(10),dp(7));t.setBackground(shape(panel(),line()));card(t);}
    Button clear=btn("CLEAR LOG");clear.setOnClickListener(v->{tap();logs.clear();show("TERMINAL");});content.addView(clear);
  }
  private void accept(int v){if(v<0||v>5000){toast("Value must be 0–5000");return;}last=v;min=Math.min(min,v);max=Math.max(max,v);samples.addLast(v);while(samples.size()>300)samples.removeFirst();logs.add(0,String.format(Locale.US,"%tT   %d",new Date(),v));if(logs.size()>200)logs.remove(logs.size()-1);if(alertSound||alertVibe){if(alertSound)tone();if(alertVibe)vibrate(45);}if(tab.equals("LIVE")||tab.equals("MANUAL")||tab.equals("AUTOMATIC"))show(tab);}
  private void acceptLine(String s){if(s.trim().equalsIgnoreCase("a")){logs.add(0,"SCAN MARKER  a");return;}try{accept(Integer.parseInt(s.trim()));}catch(Exception e){logs.add(0,"INVALID  "+s);}}
  private void chooseDevice(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},BT_REQ);return;}try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){toast("Bluetooth unavailable");return;}ArrayList<BluetoothDevice> list=new ArrayList<>(a.getBondedDevices());if(list.isEmpty()){toast("Pair the device in Android settings first");return;}String[] names=new String[list.size()];for(int i=0;i<list.size();i++)names[i]=list.get(i).getName()+" • "+list.get(i).getAddress();new AlertDialog.Builder(this).setTitle("Paired devices").setItems(names,(x,n)->connect(list.get(n))).setNegativeButton("Cancel",null).show();}catch(Exception e){toast("Bluetooth permission required");}}
  private void connect(BluetoothDevice dev){disconnect();status.setText("● CONNECTING…");reader=new Thread(()->{try{socket=dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket.connect();InputStream in=socket.getInputStream();handler.post(()->{status.setText("● CONNECTED  •  "+dev.getName()+"  •  "+baud);});ByteArrayOutputStream line=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b==10||b==13){if(line.size()>0){String s=line.toString("UTF-8").trim();line.reset();handler.post(()->acceptLine(s));}}else if(line.size()<128)line.write(b);}}catch(Exception e){handler.post(()->{status.setText("● CONNECTION LOST");toast(e.getMessage()==null?"Connection failed":e.getMessage());});}finally{try{if(socket!=null)socket.close();}catch(Exception ignored){}}});reader.start();}
  private void disconnect(){try{if(reader!=null)reader.interrupt();if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;reader=null;updateStatus();}
  private void updateStatus(){if(status!=null)status.setText("●  DEVICE DISCONNECTED     •     "+baud+" BAUD");}
  private void tap(){if(feedbackVibe)vibrate(18);if(feedbackSound)tone();}
  private void vibrate(int ms){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,65));else v.vibrate(ms);}}catch(Exception ignored){}}
  private void tone(){try{android.media.ToneGenerator t=new android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM,35);t.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,45);handler.postDelayed(t::release,100);}catch(Exception ignored){}}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==BT_REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)chooseDevice();}
  @Override protected void onDestroy(){disconnect();super.onDestroy();}
}