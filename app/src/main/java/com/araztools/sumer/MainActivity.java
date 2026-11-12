package com.araztools.sumer;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.media.*;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final ArrayDeque<Integer> samples=new ArrayDeque<>();
  private final ArrayList<Integer> activeScan=new ArrayList<>();
  private final ArrayList<Integer> lastScan=new ArrayList<>();
  private final ArrayList<String> logs=new ArrayList<>();
  private final ArrayList<Integer> pinWave=new ArrayList<>();
  private final ArrayList<File> galleryFiles=new ArrayList<>();
  private double pinBaseline=Double.NaN,pinCalibrationSum=0;
  private int pinCalibrationCount=0,pinFrequency=200;
  private long pinCalibrationStart=0,lastPinToneAt=0;
  private String lastDeviceAddress="";
  private boolean reconnectEnabled=false;
  private boolean autoReconnect=true;
  private final int[] palette={0xff18bfff,0xff7c5cff,0xff20c997,0xffffb547,0xffef5b78};
  private int accent=0xff18bfff, last=-1,min=5001,max=-1,baud=9600,scanRows=4,scanCols=3;
  private boolean dark=true,feedbackSound=true,feedbackVibe=true,alertSound=true,alertVibe=false,autoRunning=false,matrixScanActive=false;
  private String tab="HOME",alertMode="MANUAL";
  private TextView status,brand;
  private LinearLayout root,content,bottom;
  private BluetoothSocket socket;
  private Thread reader;
  private final ArrayList<BluetoothDevice> discoveredDevices=new ArrayList<>();
  private final ArrayList<String> discoveredLabels=new ArrayList<>();
  private ArrayAdapter<String> discoveredAdapter;
  private AlertDialog discoveryDialog;
  private BroadcastReceiver discoveryReceiver;
  private static final int BT_REQ=71;
  private int bg(){return dark?0xff242a30:0xffeef3f9;}
  private int panel(){return dark?0xff303840:0xffffffff;}
  private int panel2(){return dark?0xff3a444e:0xffe2eaf4;}
  private int fg(){return dark?0xfff2f7ff:0xff142238;}
  private int muted(){return dark?0xffb2bdc8:0xff62748b;}
  private int line(){return dark?0xff4b5864:0xffd2ddea;}
  private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  @Override public void onCreate(Bundle b){super.onCreate(b);android.content.SharedPreferences pref=getPreferences(0);dark=pref.getBoolean("dark",true);accent=pref.getInt("accent",0xff18bfff);feedbackSound=pref.getBoolean("feedbackSound",true);feedbackVibe=pref.getBoolean("feedbackVibe",true);alertSound=pref.getBoolean("alertSound",true);alertVibe=pref.getBoolean("alertVibe",false);baud=pref.getInt("baud",9600);scanRows=pref.getInt("scanRows",4);scanCols=pref.getInt("scanCols",3);autoReconnect=pref.getBoolean("autoReconnect",true);lastDeviceAddress=pref.getString("lastDeviceAddress","");getWindow().setNavigationBarColor(bg());getWindow().setStatusBarColor(bg());getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);if(!pref.getBoolean("activated",false)){registration();}else{shell();if(autoReconnect&&!lastDeviceAddress.isEmpty())handler.postDelayed(()->{if(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)connectAddress(lastDeviceAddress);},900);}}
  private void persistSettings(){getPreferences(0).edit().putBoolean("dark",dark).putInt("accent",accent).putBoolean("feedbackSound",feedbackSound).putBoolean("feedbackVibe",feedbackVibe).putBoolean("alertSound",alertSound).putBoolean("alertVibe",alertVibe).putInt("baud",baud).putInt("scanRows",scanRows).putInt("scanCols",scanCols).putBoolean("autoReconnect",autoReconnect).apply();}
  private TextView txt(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private void pad(View v,int a){v.setPadding(dp(a),dp(a),dp(a),dp(a));}
  private void registration(){
    LinearLayout page=col();page.setGravity(Gravity.CENTER);page.setPadding(dp(28),dp(24),dp(28),dp(24));page.setBackgroundColor(bg());setContentView(page);
    TextView logo=txt("SUMER",42,fg());logo.setGravity(Gravity.CENTER);logo.setTypeface(null,1);page.addView(logo);
    TextView sub=txt("MAGNETOMETRIC SURVEY SYSTEM",12,accent);sub.setGravity(Gravity.CENTER);sub.setLetterSpacing(.13f);page.addView(sub);
    ImageView shield=new ImageView(this);shield.setImageResource(R.drawable.ic_sumer);shield.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(125));sp.topMargin=dp(22);sp.bottomMargin=dp(8);page.addView(shield,sp);
    TextView title=txt("SERIAL REGISTRATION",19,fg());title.setGravity(Gravity.CENTER);title.setTypeface(null,1);page.addView(title);
    TextView hint=txt("Enter your personal activation code to unlock SUMER.",14,muted());hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(10),0,dp(22));page.addView(hint);
    EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(fg());input.setHintTextColor(muted());input.setHint("Enter 8-digit serial number");input.setBackground(shape(panel(),line()));pad(input,14);page.addView(input,new LinearLayout.LayoutParams(-1,dp(58)));
    TextView note=txt("One-time product registration. Enter the supplied SUMER serial number.",11,muted());note.setPadding(dp(4),dp(12),dp(4),dp(12));page.addView(note);
    Button activate=btn("ACTIVATE  →");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(54));ap.topMargin=dp(8);page.addView(activate,ap);
    TextView error=txt("",13,0xffff647c);error.setGravity(Gravity.CENTER);page.addView(error);
    activate.setOnClickListener(v->{tap();String s=input.getText().toString().trim().toUpperCase(Locale.US);if(s.equals("13651365")){getPreferences(0).edit().putBoolean("activated",true).putString("license",s).apply();welcomeTune();shell();}else {error.setText("Invalid serial format. Please check the code.");vibrate(200);}});
  }
  private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(0xffffffff);b.setAllCaps(false);b.setTextSize(14);b.setBackground(shape(accent,0));return b;}
  private TextView label(String s){TextView t=txt(s,12,muted());t.setPadding(dp(2),dp(10),dp(2),dp(5));return t;}
  private void shell(){
    getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root=col();root.setBackgroundColor(bg());setContentView(root);
    LinearLayout top=row();top.setPadding(dp(16),dp(9),dp(16),dp(9));
    brand=txt("SUMER",25,fg());brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
    root.addView(top);
    status=txt("●  DEVICE DISCONNECTED     •     9600 BAUD",11,muted());status.setPadding(dp(16),dp(10),dp(16),dp(10));status.setBackground(shape(panel(),line()));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(42));sl.setMargins(dp(14),0,dp(14),dp(10));root.addView(status,sl);status.setOnClickListener(v->{tap();show("DEVICE");});
    ScrollView scroll=new ScrollView(this);content=col();content.setPadding(dp(14),dp(4),dp(14),dp(18));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    bottom=row();bottom.setPadding(dp(8),dp(7),dp(8),dp(7));bottom.setBackgroundColor(panel());root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(60)));
    show(tab);
  }
  private void show(String name){
    tab=name;content.removeAllViews();bottom.removeAllViews();
    if(name.equals("HOME"))home();else if(name.equals("LIVE"))live();else if(name.equals("PIN"))pin();else if(name.equals("AUTOMATIC"))automatic();else if(name.equals("MANUAL"))manual();else if(name.equals("SETTINGS"))settings();else if(name.equals("DEVICE"))device();else if(name.equals("TERMINAL"))terminal();else if(name.equals("GALLERY"))gallery();
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
    LinearLayout r1=row();r1.addView(tile("◉","LIVE","Real-time view","LIVE"),new LinearLayout.LayoutParams(0,dp(154),1));space(r1);r1.addView(tile("⌖","PIN","Point survey","PIN"),new LinearLayout.LayoutParams(0,dp(154),1));LinearLayout.LayoutParams r1p=new LinearLayout.LayoutParams(-1,-2);r1p.bottomMargin=dp(10);content.addView(r1,r1p);
    LinearLayout r2=row();r2.addView(tile("▷","AUTOMATIC","Automatic scan","AUTOMATIC"),new LinearLayout.LayoutParams(0,dp(154),1));space(r2);r2.addView(tile("✥","MANUAL","Manual survey","MANUAL"),new LinearLayout.LayoutParams(0,dp(154),1));content.addView(r2,new LinearLayout.LayoutParams(-1,-2));
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
    LinearLayout r=row();String[] heads={"CURRENT","MIN","MAX"};String[] vals={last<0?"—":""+last,min>5000?"—":""+min,max<0?"—":""+max};
    for(int i=0;i<3;i++){LinearLayout c=col();c.setGravity(Gravity.CENTER);c.setPadding(dp(5),dp(10),dp(5),dp(10));c.setBackground(shape(panel(),line()));TextView h=txt(heads[i],9,muted());h.setGravity(Gravity.CENTER);c.addView(h);TextView v=txt(vals[i],18,i==0?accent:fg());v.setGravity(Gravity.CENTER);v.setTypeface(null,1);c.addView(v);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(66),1);cp.setMargins(dp(2),0,dp(2),0);r.addView(c,cp);}content.addView(r);
  }
  private void live(){
    content.addView(heading("LIVE MONITOR"));content.addView(txt("One live block • previous frame is replaced",12,muted()));stats();
    TextView block=txt(last<0?"WAITING FOR DATA":""+last,36,0xffffffff);block.setGravity(Gravity.CENTER);block.setTypeface(null,1);block.setBackground(shape(scanColor(last<0?0:last,min>5000?0:min,max<0?5000:max),0));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(190));bp.topMargin=dp(14);bp.bottomMargin=dp(12);content.addView(block,bp);
    
    testDataInput("MANUAL LIVE DATA INPUT");
    Button pause=btn("CLEAR LIVE MIN / MAX");pause.setOnClickListener(v->{tap();min=5001;max=-1;last=-1;show("LIVE");});content.addView(pause);
  }
  private void manual(){
    content.addView(heading("MANUAL SURVEY"));
    content.addView(txt("Zigzag matrix survey • starts at bottom-left and proceeds column by column.",12,muted()));
    galleryButton();
    stats();scanControls();scanTestButton();testDataInput("MANUAL TEST DATA INPUT");
    scanVisualization();
    content.addView(heading("RECENT VALUES"));
    for(Integer n:samples){TextView t=txt("●     "+n,14,fg());t.setPadding(dp(12),dp(10),dp(12),dp(10));card(t);}
  }
  private void scanControls(){
    content.addView(heading("SCAN GRID"));
    LinearLayout grid=row();EditText rowsField=numberField("Rows",scanRows);EditText colsField=numberField("Columns",scanCols);
    grid.addView(rowsField,new LinearLayout.LayoutParams(0,dp(54),1));View gap=new View(this);grid.addView(gap,new LinearLayout.LayoutParams(dp(10),1));grid.addView(colsField,new LinearLayout.LayoutParams(0,dp(54),1));content.addView(grid);
    Button save=btn("APPLY ROWS / COLUMNS");save.setOnClickListener(v->{tap();try{int r=Integer.parseInt(rowsField.getText().toString().trim()),c=Integer.parseInt(colsField.getText().toString().trim());if(r<1||r>50||c<1||c>50||r*c>2500)throw new Exception();scanRows=r;scanCols=c;persistSettings();toast("Grid set to "+r+" rows × "+c+" columns");show(tab);}catch(Exception e){toast("Rows and columns must be 1–50");}});content.addView(save);
    TextView dimensions=txt("ACTIVE GRID: "+scanRows+" × "+scanCols+"  •  "+(scanRows*scanCols)+" CELLS",11,accent);dimensions.setPadding(dp(4),dp(8),dp(4),dp(10));content.addView(dimensions);
  }
  private void scanTestButton(){
    Button test=btn(matrixScanActive?"FINISH TEST SCAN  •  "+activeScan.size()+" VALUES":"START TEST SCAN");
    test.setOnClickListener(v->{tap();if(matrixScanActive){finishMatrixScan();}else{matrixScanActive=true;activeScan.clear();lastScan.clear();toast("Test scan started. Enter values in zigzag order.");}show(tab);});content.addView(test);
  }
  private EditText numberField(String hint,int value){EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(2);e.setText(""+value);e.setHint(hint);e.setTextColor(fg());e.setHintTextColor(muted());e.setBackground(shape(panel(),line()));pad(e,12);return e;}
  private void testDataInput(String title){
    content.addView(heading(title));LinearLayout panelBox=col();panelBox.setPadding(dp(12),dp(10),dp(12),dp(12));panelBox.setBackground(shape(panel(),line()));
    panelBox.addView(txt("Enter a test reading (0–5000) as if it arrived from the scanner.",11,muted()));
    EditText data=numberField("Received value",0);data.setText("");LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,dp(54));dp1.topMargin=dp(8);panelBox.addView(data,dp1);
    Button inject=btn("INJECT TEST DATA");inject.setOnClickListener(v->{tap();try{int n=Integer.parseInt(data.getText().toString().trim());if(n<0||n>5000)throw new Exception();accept(n);data.setText("");toast("Test value received: "+n);}catch(Exception e){toast("Enter an integer from 0 to 5000");}});
    LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(8);panelBox.addView(inject,bp);card(panelBox);
  }
  private void automatic(){
    content.addView(heading("AUTOMATIC SURVEY"));content.addView(txt("Automatic acquisition • zigzag path from the bottom-left corner.",12,muted()));galleryButton();stats();scanControls();scanTestButton();
    TextView state=txt(autoRunning?"●  ACQUISITION ACTIVE":"○  READY",17,autoRunning?0xff20c997:muted());state.setGravity(Gravity.CENTER);state.setBackground(shape(panel(),line()));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));p.topMargin=dp(12);content.addView(state,p);
    Button b=btn(autoRunning?"STOP SESSION":"START SESSION");b.setOnClickListener(v->{tap();if(!autoRunning){autoRunning=true;matrixScanActive=false;activeScan.clear();lastScan.clear();toast("Automatic session ready — waiting for scan marker");}else{if(matrixScanActive)finishMatrixScan();autoRunning=false;toast("Automatic scan session stopped");}show("AUTOMATIC");});content.addView(b);
    testDataInput("MANUAL TEST DATA RECEIVER");
    scanVisualization();
    content.addView(txt("Test values enter the same display pipeline; no synthetic readings are generated automatically.",11,muted()));
  }
  private void pin(){
    content.addView(heading("PIN POINT SURVEY"));
    content.addView(txt("First 2 seconds establish the baseline. The sine trace and square-wave tone respond to deviations.",12,muted()));
    stats();
    String base=Double.isNaN(pinBaseline)?"CALIBRATING — "+pinCalibrationCount+" samples":String.format(Locale.US,"BASELINE %.1f  •  CURRENT Δ %+d  •  TONE %d Hz",pinBaseline,last<0?0:(int)Math.round(last-pinBaseline),pinFrequency);
    TextView info=txt(base,12,accent);info.setPadding(dp(8),dp(12),dp(8),dp(12));card(info);
    View wave=new View(this){protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();Paint p=new Paint(3);p.setColor(line());p.setStrokeWidth(dp(1));canvas.drawLine(0,h/2,w,h/2,p);p.setColor(accent);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);Path path=new Path();int n=Math.max(80,pinWave.size());for(int i=0;i<n;i++){float x=(w-8)*i/Math.max(1,n-1)+4;float amp=dp(12);if(!pinWave.isEmpty()){int v=pinWave.get(Math.min(pinWave.size()-1,(int)((long)i*pinWave.size()/n)));amp=Math.min(h*.42f,dp(12)+Math.abs(v)*h/10000f);}float y=h/2-(float)Math.sin(i*.18)*amp;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);}};
    wave.setBackground(shape(panel(),line()));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(150));wp.bottomMargin=dp(12);content.addView(wave,wp);
    EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Point name / ID");name.setTextColor(fg());name.setHintTextColor(muted());name.setBackground(shape(panel(),line()));pad(name,12);content.addView(name);
    Button b=btn("SAVE CURRENT VALUE AS POINT");b.setOnClickListener(v->{tap();if(last<0){toast("No measurement received yet");return;}String n=name.getText().toString().trim();if(n.isEmpty())n="Point "+(pinRecords().size()+1);ArrayList<String> points=pinRecords();points.add(0,n+" | "+last+" | "+new Date());getPreferences(0).edit().putString("pinRecords",joinLines(points)).apply();toast("Point saved permanently");show("PIN");});content.addView(b);
    testDataInput("MANUAL PIN DATA INPUT");
    content.addView(heading("SAVED POINTS"));for(String p:pinRecords()){TextView t=txt("⌖   "+p,13,fg());t.setPadding(dp(10),dp(9),dp(10),dp(9));card(t);}
  }
  private int scanColor(int value,int lo,int hi){
    float t=hi<=lo?0.5f:Math.max(0f,Math.min(1f,(value-lo)/(float)(hi-lo)));
    int[] stops={0xff174bdb,0xff00b9d6,0xff16b86a,0xffffc629,0xffed3b32};
    float p=t*(stops.length-1);int k=Math.min(stops.length-2,(int)p);float u=p-k;
    int a=stops[k],b=stops[k+1];int r=(int)(((a>>16)&255)*(1-u)+((b>>16)&255)*u),g=(int)(((a>>8)&255)*(1-u)+((b>>8)&255)*u),bl=(int)((a&255)*(1-u)+(b&255)*u);
    return 0xff000000|(r<<16)|(g<<8)|bl;
  }
  private ArrayList<Integer> scanDisplayValues(){return new ArrayList<>(matrixScanActive?activeScan:lastScan);}
  private void scanVisualization(){
    ArrayList<Integer> values=scanDisplayValues();
    int lo=5000,hi=0;for(int v:values){lo=Math.min(lo,v);hi=Math.max(hi,v);}
    if(values.isEmpty()){lo=0;hi=5000;}
    content.addView(heading("SCAN COLOUR MAP • "+values.size()+" / "+(scanRows*scanCols)+" VALUES"));
    content.addView(txt("MIN "+(values.isEmpty()?"—":lo)+"   MAX "+(values.isEmpty()?"—":hi)+"   GRID "+scanRows+" × "+scanCols+"   •   ZIGZAG FROM BOTTOM-LEFT",11,muted()));
    final ArrayList<Integer> data=values;final int low=lo,high=hi;
    View map=new View(this){
      protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws);float cell=Math.min(width/(float)scanCols,dp(500)/(float)scanRows);setMeasuredDimension(width,(int)(cell*scanRows));}
      protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float cell=Math.min(getWidth()/(float)scanCols,dp(500)/(float)scanRows);float offset=(getWidth()-cell*scanCols)/2;
        Paint p=new Paint(3);p.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<scanRows*scanCols;i++){
          int[] rc=scanCell(i);float x=offset+rc[1]*cell,y=rc[0]*cell;
          if(i<data.size()){p.setColor(scanColor(data.get(i),low,high));canvas.drawRect(x,y,x+cell,y+cell,p);}
          p.setColor(0x887f929f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));canvas.drawRect(x+dp(.5f),y+dp(.5f),x+cell-dp(.5f),y+cell-dp(.5f),p);p.setStyle(Paint.Style.FILL);
          if(i<data.size()){
            p.setColor(0xffffffff);p.setTextSize(dp(9));canvas.drawText("#"+(i+1),x+cell/2,y+cell*.42f,p);
            p.setTextSize(dp(13));canvas.drawText(""+data.get(i),x+cell/2,y+cell*.72f,p);
          }
        }
      }
    };
    map.setBackgroundColor(panel());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(10);content.addView(map,mp);
    if(values.isEmpty())return;
    content.addView(heading("SCAN GRAPH"));
    View chart=new View(this){protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;Paint p=new Paint(3);p.setColor(line());p.setStrokeWidth(dp(1));for(int i=1;i<4;i++)canvas.drawLine(0,h*i/4,w,h*i/4,p);p.setColor(accent);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);Path path=new Path();for(int i=0;i<data.size();i++){float x=data.size()==1?w/2:(w-8)*i/(data.size()-1)+4;float y=h-4-(data.get(i)/5000f)*(h-8);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);}};
    chart.setBackground(shape(panel(),line()));LinearLayout.LayoutParams chartP=new LinearLayout.LayoutParams(-1,dp(150));chartP.bottomMargin=dp(14);content.addView(chart,chartP);
  }
  private void settings(){
    content.addView(heading("SETTINGS"));content.addView(label("APPEARANCE"));
    addChoice("Theme",new String[]{"Dark","Light"},dark?0:1,i->{dark=i==0;persistSettings();shell();});
    addChoice("Accent colour",new String[]{"Ice blue","Violet","Emerald","Amber","Rose"},paletteIndex(),i->{accent=palette[i];persistSettings();shell();});
    content.addView(label("TOUCH FEEDBACK"));
    addChoice("Feedback",new String[]{"Sound","Vibration","Both","None"},feedbackSound&&feedbackVibe?2:feedbackSound?0:feedbackVibe?1:3,i->{feedbackSound=i==0||i==2;feedbackVibe=i==1||i==2;persistSettings();});
    content.addView(label("SIGNAL ALERTS  •  MANUAL"));
    addChoice("Alert output",new String[]{"Speaker","Vibration","Both","None"},alertSound&&alertVibe?2:alertSound?0:alertVibe?1:3,i->{alertSound=i==0||i==2;alertVibe=i==1||i==2;persistSettings();});
    content.addView(label("CONNECTION"));
    addChoice("Baud rate",new String[]{"1200","2400","4800","9600","19200","38400","57600","115200"},baudIndex(),i->{int[] a={1200,2400,4800,9600,19200,38400,57600,115200};baud=a[i];persistSettings();updateStatus();});
    addChoice("Automatic reconnect",new String[]{"On","Off"},autoReconnect?0:1,i->{autoReconnect=i==0;persistSettings();});
    Button testFeedback=btn("TEST TOUCH FEEDBACK");
    testFeedback.setOnClickListener(v->{tap();toast("Touch feedback test complete");});content.addView(testFeedback);
    Button testAlert=btn("TEST SIGNAL ALERT");
    testAlert.setOnClickListener(v->{tap();if(alertVibe)vibrate(250);if(alertSound)tone();if(!alertVibe&&!alertSound)toast("Signal alert is disabled");});content.addView(testAlert);
    TextView about=txt("SUMER • MAGNETOMETRIC SURVEY SYSTEM\nSingle-channel prototype • Android",12,muted());about.setPadding(dp(4),dp(20),dp(4),dp(20));content.addView(about);
  }
  private int paletteIndex(){for(int i=0;i<palette.length;i++)if(palette[i]==accent)return i;return 0;}
  private int baudIndex(){int[] a={1200,2400,4800,9600,19200,38400,57600,115200};for(int i=0;i<a.length;i++)if(a[i]==baud)return i;return 3;}
  interface Pick{void choose(int i);}
  private void addChoice(String title,String[] opts,int selected,Pick pick){
    LinearLayout box=col();box.setPadding(dp(13),dp(8),dp(13),dp(8));box.setBackground(shape(panel(),line()));
    TextView t=txt(title,13,fg());box.addView(t);
    Spinner sp=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);
    sp.setAdapter(a);sp.setSelection(Math.max(0,Math.min(selected,opts.length-1)));
    sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      private int previous=Math.max(0,Math.min(selected,opts.length-1));
      public void onNothingSelected(android.widget.AdapterView<?>p){}
      public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){
        if(pos==previous)return;previous=pos;pick.choose(pos);tap();
      }
    });
    box.addView(sp);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);content.addView(box,p);
  }
  private void device(){
    content.addView(heading("DEVICE CONNECTION"));content.addView(txt("Bluetooth Classic / SPP • paired devices",12,muted()));
    Button c=btn("SEARCH / SELECT BLUETOOTH DEVICE");c.setOnClickListener(v->{tap();chooseDevice();});content.addView(c);
    Button d=btn("DISCONNECT");d.setOnClickListener(v->{tap();disconnect();});content.addView(d);
    addChoice("Baud rate",new String[]{"1200","2400","4800","9600","19200","38400","57600","115200"},baudIndex(),i->{int[] a={1200,2400,4800,9600,19200,38400,57600,115200};baud=a[i];persistSettings();updateStatus();});
    content.addView(txt("Pair the scanner in Android Bluetooth settings first. The baud selection is a project setting and must match the transmitter configuration.",11,muted()));
  }
  private void terminal(){
    content.addView(heading("DATA TERMINAL"));content.addView(txt("Raw incoming and outgoing Bluetooth SPP data • one value per line",12,muted()));
    LinearLayout send=row();EditText command=new EditText(this);command.setSingleLine(true);command.setInputType( android.text.InputType.TYPE_CLASS_TEXT);command.setHint("Send command / value (e.g. a)");command.setTextColor(fg());command.setHintTextColor(muted());command.setBackground(shape(panel(),line()));pad(command,10);send.addView(command,new LinearLayout.LayoutParams(0,dp(52),1));Button sendBtn=btn("SEND");send.addView(sendBtn);sendBtn.setOnClickListener(v->{tap();String value=command.getText().toString().trim();if(value.isEmpty())return;sendLine(value);logs.add(0,"TX  "+value);command.setText("");show("TERMINAL");});content.addView(send);
    for(String s:logs){TextView t=txt(s,13,0xff20c997);t.setPadding(dp(10),dp(7),dp(10),dp(7));t.setBackground(shape(panel(),line()));card(t);}
    Button clear=btn("CLEAR LOG");clear.setOnClickListener(v->{tap();logs.clear();show("TERMINAL");});content.addView(clear);
  }
  private void accept(int v){
    if(v<0||v>5000){toast("Value must be 0–5000");return;}
    last=v;min=Math.min(min,v);max=Math.max(max,v);
    if(matrixScanActive){int idx=activeScan.size();if(idx<scanRows*scanCols){activeScan.add(v);int col=idx/scanRows;signalAlert(col==scanCols-1?500:200);}}
    if(!tab.equals("LIVE")){samples.addLast(v);while(samples.size()>300)samples.removeFirst();logs.add(0,String.format(Locale.US,"%tT   RX %d",new Date(),v));if(logs.size()>200)logs.remove(logs.size()-1);}
    if(tab.equals("PIN"))updatePinSignal(v);
    if((alertSound||alertVibe)&&!matrixScanActive&& !tab.equals("PIN"))signalAlert(45);
    if(tab.equals("LIVE")||tab.equals("MANUAL")||tab.equals("AUTOMATIC")||tab.equals("PIN"))requestRefresh();
  }
  private void acceptLine(String s){
    if(s.trim().equalsIgnoreCase("a")){if(matrixScanActive){finishMatrixScan();}else{matrixScanActive=true;activeScan.clear();lastScan.clear();if(!tab.equals("LIVE"))logs.add(0,"MATRIX SCAN START");}if(tab.equals("MANUAL")||tab.equals("AUTOMATIC")||tab.equals("PIN"))show(tab);return;}
    try{accept(Integer.parseInt(s.trim()));}catch(Exception e){if(!tab.equals("LIVE")){logs.add(0,"INVALID  "+s);if(logs.size()>200)logs.remove(logs.size()-1);}}
  }
  private void chooseDevice(){searchDevices();}
  @SuppressWarnings("MissingPermission") private void searchDevices(){
    if(Build.VERSION.SDK_INT>=31&&(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)){
      requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},BT_REQ);return;
    }
    try{
      BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
      if(adapter==null){toast("Bluetooth unavailable");return;}
      if(!adapter.isEnabled()){toast("Turn on Bluetooth first");return;}
      stopDiscovery();
      discoveredDevices.clear();discoveredLabels.clear();
      for(BluetoothDevice d:adapter.getBondedDevices())addDiscovered(d,"PAIRED");
      discoveredAdapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,discoveredLabels);
      discoveryDialog=new AlertDialog.Builder(this).setTitle("Bluetooth devices — searching").setAdapter(discoveredAdapter,(dialog,which)->{
        BluetoothDevice selected=discoveredDevices.get(which);stopDiscovery();connect(selected);
      }).setNegativeButton("Close",(dialog,which)->stopDiscovery()).create();
      discoveryDialog.setOnDismissListener(d->{stopDiscovery();});
      discoveryDialog.show();
      discoveryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(BluetoothDevice.ACTION_FOUND.equals(action)){
          BluetoothDevice d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
          if(d!=null)addDiscovered(d,"FOUND");
        }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
          if(discoveryDialog!=null&&discoveryDialog.isShowing())discoveryDialog.setTitle("Bluetooth devices — search complete");
          stopDiscoveryReceiverOnly();
        }
      }};
      IntentFilter filter=new IntentFilter();filter.addAction(BluetoothDevice.ACTION_FOUND);filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
      if(Build.VERSION.SDK_INT>=33)registerReceiver(discoveryReceiver,filter,Context.RECEIVER_EXPORTED);else registerReceiver(discoveryReceiver,filter);
      adapter.startDiscovery();toast("Searching for nearby Bluetooth devices…");
      handler.postDelayed(()->{try{if(adapter.isDiscovering())adapter.cancelDiscovery();if(discoveryDialog!=null&&discoveryDialog.isShowing())discoveryDialog.setTitle("Bluetooth devices — search complete");}catch(Exception ignored){}stopDiscoveryReceiverOnly();},12000);
    }catch(Exception e){toast("Bluetooth search failed: "+e.getMessage());}
  }
  @SuppressWarnings("MissingPermission") private void addDiscovered(BluetoothDevice d,String tag){
    try{String address=d.getAddress();for(BluetoothDevice old:discoveredDevices)if(old.getAddress().equals(address))return;discoveredDevices.add(d);String name=d.getName();discoveredLabels.add((name==null||name.isEmpty()?"Bluetooth device":name)+" • "+address+" • "+tag);if(discoveredAdapter!=null)discoveredAdapter.notifyDataSetChanged();}catch(Exception ignored){}
  }
  private void stopDiscoveryReceiverOnly(){if(discoveryReceiver!=null){try{unregisterReceiver(discoveryReceiver);}catch(Exception ignored){}discoveryReceiver=null;}}
  @SuppressWarnings("MissingPermission") private void stopDiscovery(){try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&a.isDiscovering())a.cancelDiscovery();}catch(Exception ignored){}stopDiscoveryReceiverOnly();}
  private void connect(BluetoothDevice dev){
    reconnectEnabled=true;lastDeviceAddress=dev.getAddress();getPreferences(0).edit().putString("lastDeviceAddress",lastDeviceAddress).apply();
    disconnectSocketOnly();status.setText("● CONNECTING…");
    final BluetoothSocket localSocket;
    try{localSocket=dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket=localSocket;}
    catch(Exception e){status.setText("● CONNECTION FAILED");toast(e.getMessage()==null?"Connection failed":e.getMessage());scheduleReconnect();return;}
    reader=new Thread(()->{
      try{
        localSocket.connect();
        if(socket!=localSocket){localSocket.close();return;}
        InputStream in=localSocket.getInputStream();
        handler.post(()->{if(socket==localSocket)status.setText("● CONNECTED  •  "+dev.getName()+"  •  "+baud);});
        ByteArrayOutputStream line=new ByteArrayOutputStream();int b;
        while(!Thread.currentThread().isInterrupted()&&(b=in.read())!=-1){
          if(b==10||b==13){
            if(line.size()>0){String value=line.toString("UTF-8").trim();line.reset();handler.post(()->{if(socket==localSocket)acceptLine(value);});}
          }else if(line.size()<128)line.write(b);
        }
      }catch(Exception e){
        handler.post(()->{if(socket==localSocket){status.setText("● CONNECTION LOST");toast(e.getMessage()==null?"Connection failed":e.getMessage());scheduleReconnect();}});
      }finally{
        try{localSocket.close();}catch(Exception ignored){}
        if(socket==localSocket){socket=null;handler.post(()->{if(status!=null)status.setText("● CONNECTION CLOSED");scheduleReconnect();});}
      }
    });reader.start();
  }
  private void disconnectSocketOnly(){try{if(reader!=null)reader.interrupt();if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;reader=null;}
  private void disconnect(){reconnectEnabled=false;disconnectSocketOnly();updateStatus();}
  private void scheduleReconnect(){if(!autoReconnect||!reconnectEnabled||lastDeviceAddress.isEmpty())return;handler.postDelayed(()->{if(reconnectEnabled&&socket==null)connectAddress(lastDeviceAddress);},5000);}
  @SuppressWarnings("MissingPermission") private void connectAddress(String address){try{BluetoothDevice d=BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);connect(d);}catch(Exception e){scheduleReconnect();}}
  private void updateStatus(){if(status!=null)status.setText("●  DEVICE DISCONNECTED     •     "+baud+" BAUD");}
  private void tap(){if(feedbackVibe)vibrate(18);if(feedbackSound)tone();}
  private void signalAlert(int duration){if(alertVibe)vibrate(duration);if(alertSound)toneFor(duration);}
  private void toneFor(int duration){try{ToneGenerator t=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,65);t.startTone(ToneGenerator.TONE_PROP_BEEP,duration);handler.postDelayed(t::release,duration+80);}catch(Exception ignored){}}
  private void updatePinSignal(int value){long now=SystemClock.elapsedRealtime();if(pinCalibrationStart==0)pinCalibrationStart=now;if(now-pinCalibrationStart<2000){pinCalibrationSum+=value;pinCalibrationCount++;pinBaseline=pinCalibrationSum/pinCalibrationCount;pinFrequency=200;}else{if(Double.isNaN(pinBaseline))pinBaseline=pinCalibrationCount==0?value:pinCalibrationSum/pinCalibrationCount;double delta=value-pinBaseline;pinFrequency=Math.max(1,Math.min(400,(int)Math.round(200+delta*199/5000.0)));pinWave.add((int)Math.max(-5000,Math.min(5000,Math.round(delta))));while(pinWave.size()>120)pinWave.remove(0);if(alertVibe)vibrate(35);if(alertSound&&now-lastPinToneAt>100){lastPinToneAt=now;playSquareTone(pinFrequency,80);}}}
  private void playSquareTone(int frequency,int durationMs){new Thread(()->{AudioTrack track=null;try{int rate=22050,count=rate*durationMs/1000;short[] pcm=new short[count];int half=Math.max(1,rate/(2*Math.max(1,frequency)));for(int i=0;i<count;i++)pcm[i]=(short)((i/half%2==0?1:-1)*3500);track=new AudioTrack(AudioManager.STREAM_MUSIC,rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,count*2,AudioTrack.MODE_STATIC);track.write(pcm,0,count);track.play();Thread.sleep(durationMs+20);}catch(Exception ignored){}finally{if(track!=null)try{track.stop();track.release();}catch(Exception ignored){}}}).start();}
  private void vibrate(int ms){try{android.os.Vibrator v=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,65));else v.vibrate(ms);}}catch(Exception ignored){}}
  private void tone(){try{android.media.ToneGenerator t=new android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM,35);t.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,45);handler.postDelayed(t::release,100);}catch(Exception ignored){}}
  private void welcomeTune(){
    new Thread(()->{android.media.AudioTrack track=null;try{
      final int rate=22050,total=rate*2;short[] pcm=new short[total];double[] notes={523.25,659.25,783.99,1046.50};
      for(int i=0;i<total;i++){int segment=Math.min(3,i/(rate/2));double local=(i%(rate/2))/(double)rate;double envelope=Math.min(1.0,local*18.0)*Math.min(1.0,(0.5-local)*18.0);pcm[i]=(short)(Math.sin(2*Math.PI*notes[segment]*i/rate)*envelope*5000);}
      track=new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC,rate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT,total*2,android.media.AudioTrack.MODE_STATIC);
      track.write(pcm,0,pcm.length);track.play();Thread.sleep(2050);
    }catch(Exception ignored){}finally{if(track!=null)try{track.stop();track.release();}catch(Exception ignored){}}}).start();
  }

  private boolean refreshPending=false;
  private void requestRefresh(){if(refreshPending)return;refreshPending=true;handler.postDelayed(()->{refreshPending=false;if(tab.equals("LIVE")||tab.equals("MANUAL")||tab.equals("AUTOMATIC")||tab.equals("PIN"))show(tab);},60);}
  private void finishMatrixScan(){matrixScanActive=false;autoRunning=false;lastScan.clear();lastScan.addAll(activeScan);activeScan.clear();if(!lastScan.isEmpty())saveScanToGallery();if(!tab.equals("LIVE"))logs.add(0,"MATRIX SCAN END • "+lastScan.size()+" values");}
  private int[] scanCell(int index){int col=index/scanRows,offset=index%scanRows;int row=(col%2==0)?scanRows-1-offset:offset;return new int[]{row,col};}
  private void galleryButton(){Button g=btn("OPEN SAVED SCAN GALLERY");g.setOnClickListener(v->{tap();show("GALLERY");});content.addView(g);}
  private void saveScanToGallery(){
    try{
      File dir=new File(getFilesDir(),"scans");if(!dir.exists())dir.mkdirs();
      String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
      String[] suffix={"_01_HOMOGENEOUS","_02_GRID","_03_LABELS","_04_3D_THREE_VIEWS"};
      for(int mode=0;mode<4;mode++){
        Bitmap bitmap=mode==3?renderPerspectiveScan(lastScan,scanRows,scanCols):renderFlatScan(lastScan,scanRows,scanCols,mode);
        File png=new File(dir,"SUMER_"+stamp+suffix[mode]+".png");
        try(FileOutputStream out=new FileOutputStream(png)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
      }
      File csv=new File(dir,"SUMER_"+stamp+".csv");
      try(FileWriter w=new FileWriter(csv)){w.write("row,column,block,value"+System.lineSeparator());for(int i=0;i<lastScan.size();i++){int[] rc=scanCell(i);w.write((rc[0]+1)+","+(rc[1]+1)+","+(i+1)+","+lastScan.get(i)+System.lineSeparator());}}
      toast("4 scan images and CSV saved to SUMER gallery");
    }catch(Exception e){toast("Could not save scan: "+e.getMessage());}
  }
  private int[] scanRange(ArrayList<Integer> values){int lo=5000,hi=0;for(int v:values){lo=Math.min(lo,v);hi=Math.max(hi,v);}return new int[]{lo,hi};}
  private Bitmap renderFlatScan(ArrayList<Integer> values,int rows,int cols,int mode){
    int cell=Math.max(48,Math.min(180,2048/Math.max(rows,cols)));
    Bitmap b=Bitmap.createBitmap(cols*cell,rows*cell,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xff202832);
    int[] range=scanRange(values);Paint p=new Paint(3);p.setTextAlign(Paint.Align.CENTER);
    for(int i=0;i<values.size()&&i<rows*cols;i++){
      int[] rc=scanCell(i);float x=rc[1]*cell,y=rc[0]*cell;
      p.setColor(scanColor(values.get(i),range[0],range[1]));c.drawRect(x,y,x+cell,y+cell,p);
      if(mode>=1){p.setColor(0x887f929f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(mode==1?1.2f:1.5f);c.drawRect(x+.5f,y+.5f,x+cell-.5f,y+cell-.5f,p);p.setStyle(Paint.Style.FILL);}
      if(mode==2){p.setColor(0xffffffff);p.setTextSize(Math.max(10,cell*.13f));c.drawText("#"+(i+1),x+cell/2,y+cell*.43f,p);p.setTextSize(Math.max(12,cell*.2f));c.drawText(""+values.get(i),x+cell/2,y+cell*.72f,p);}
    }
    return b;
  }
  private void drawSurfaceTriangle(Canvas canvas,Paint paint,float[] a,float[] b,float[] c,int color){
    paint.setColor(color);paint.setStyle(Paint.Style.FILL);Path path=new Path();path.moveTo(a[0],a[1]);path.lineTo(b[0],b[1]);path.lineTo(c[0],c[1]);path.close();canvas.drawPath(path,paint);
  }
  private float[] projectPoint(float x,float y,float z,float angle,float cx,float baseY,float scale,float heightScale){
    float rx=(float)(x*Math.cos(angle)-y*Math.sin(angle));float ry=(float)(x*Math.sin(angle)+y*Math.cos(angle));
    return new float[]{cx+(rx-ry)*scale*.72f,baseY+(rx+ry)*scale*.34f-z*heightScale};
  }
  private void drawPerspectiveView(Canvas canvas,ArrayList<Integer> values,int rows,int cols,float left,float top,float width,float height,float angle,int low,int high){
    float extent=Math.max(rows,cols);float scale=Math.min(width/(extent*1.55f),height/(extent*.9f));float cx=left+width/2,baseY=top+height*.76f;
    float hScale=height*.42f;Paint p=new Paint(3);
    for(int r=0;r<rows-1;r++)for(int col=0;col<cols-1;col++){
      int i00=findScanIndex(r,col,rows),i10=findScanIndex(r+1,col,rows),i01=findScanIndex(r,col+1,rows),i11=findScanIndex(r+1,col+1,rows);
      float z00=scanValueAt(values,i00)/5000f,z10=scanValueAt(values,i10)/5000f,z01=scanValueAt(values,i01)/5000f,z11=scanValueAt(values,i11)/5000f;
      float[] a=projectPoint(col,r,z00,angle,cx,baseY,scale,hScale),bb=projectPoint(col,r+1,z10,angle,cx,baseY,scale,hScale),cc=projectPoint(col+1,r,z01,angle,cx,baseY,scale,hScale),dd=projectPoint(col+1,r+1,z11,angle,cx,baseY,scale,hScale);
      int v=(scanValueAt(values,i00)+scanValueAt(values,i10)+scanValueAt(values,i01)+scanValueAt(values,i11))/4;int color=scanColor(v,low,high);
      drawSurfaceTriangle(canvas,p,a,bb,cc,color);drawSurfaceTriangle(canvas,p,bb,dd,cc,color);
      p.setColor(0x668899aa);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);Path outline=new Path();outline.moveTo(a[0],a[1]);outline.lineTo(bb[0],bb[1]);outline.lineTo(dd[0],dd[1]);outline.lineTo(cc[0],cc[1]);outline.close();canvas.drawPath(outline,p);p.setStyle(Paint.Style.FILL);
    }
  }
  private int findScanIndex(int row,int col,int rows){return col*rows+((col%2==0)?rows-1-row:row);}
  private int scanValueAt(ArrayList<Integer> values,int index){return index>=0&&index<values.size()?values.get(index):0;}
  private Bitmap renderPerspectiveScan(ArrayList<Integer> values,int rows,int cols){
    int panelW=700,panelH=540;Bitmap b=Bitmap.createBitmap(panelW*3,panelH,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xff202832);
    int[] range=scanRange(values);Paint p=new Paint(3);p.setColor(0xfff2f7ff);p.setTextSize(28);p.setTextAlign(Paint.Align.CENTER);
    String[] names={"VIEW 1","VIEW 2","VIEW 3"};double[] angles={0.35,0.78,1.2};
    for(int i=0;i<3;i++){float left=i*panelW;c.drawText(names[i],left+panelW/2,38,p);drawPerspectiveView(c,values,rows,cols,left+10,50,panelW-20,panelH-60,(float)angles[i],range[0],range[1]);if(i>0){p.setColor(0xff65717d);p.setStrokeWidth(2);c.drawLine(left,0,left,panelH,p);p.setColor(0xfff2f7ff);}}
    return b;
  }
  private void gallery(){content.addView(heading("SAVED SCAN GALLERY"));content.addView(txt("Completed matrix scans are saved on this phone. Select EXPORT to copy an image to the device gallery.",12,muted()));File dir=new File(getFilesDir(),"scans");File[] files=dir.listFiles((d,n)->n.endsWith(".png"));galleryFiles.clear();if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));galleryFiles.addAll(Arrays.asList(files));}if(galleryFiles.isEmpty()){content.addView(txt("No saved scans yet.",14,muted()));return;}for(File f:galleryFiles){LinearLayout box=col();box.setPadding(dp(10),dp(10),dp(10),dp(10));box.setBackground(shape(panel(),line()));TextView name=txt(f.getName(),12,fg());box.addView(name);ImageView image=new ImageView(this);Bitmap bm=BitmapFactory.decodeFile(f.getAbsolutePath());image.setImageBitmap(bm);image.setAdjustViewBounds(true);box.addView(image,new LinearLayout.LayoutParams(-1,dp(210)));Button export=btn("EXPORT IMAGE TO PHONE GALLERY");export.setOnClickListener(v->{tap();exportImage(f);});box.addView(export);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);content.addView(box,p);}}
  private void exportImage(File file){try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,file.getName());values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/SUMER");Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("MediaStore unavailable");try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}toast("Image exported to Pictures/SUMER");}catch(Exception e){toast("Export failed: "+e.getMessage());}}
  private ArrayList<String> pinRecords(){ArrayList<String> out=new ArrayList<>();String raw=getPreferences(0).getString("pinRecords","");if(!raw.isEmpty())out.addAll(Arrays.asList(raw.split("\\n")));return out;}
  private String joinLines(ArrayList<String> lines){StringBuilder b=new StringBuilder();for(String line:lines){if(b.length()>0)b.append((char)10);b.append(line.replace((char)10,' '));}return b.toString();}
  private void sendLine(String value){try{if(socket==null||!socket.isConnected()){toast("Device is not connected");return;}OutputStream out=socket.getOutputStream();out.write((value+System.lineSeparator()).getBytes("UTF-8"));out.flush();}catch(Exception e){toast("Send failed: "+e.getMessage());}}

  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==BT_REQ&&g.length>=2&&g[0]==PackageManager.PERMISSION_GRANTED&&g[1]==PackageManager.PERMISSION_GRANTED)searchDevices();}
  @Override protected void onDestroy(){stopDiscovery();disconnect();super.onDestroy();}
}