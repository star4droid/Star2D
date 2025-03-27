package com.star4droid.star2d.Views;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import android.widget.Toast;
import androidx.core.widget.TextViewCompat;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.Utils;
import com.star4droid.star2d.evo.R;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisualScriptingView extends LinearLayout {

  private final HashMap<String, Object> map = new HashMap<>();
  private final HashMap<String, Object> codesMap = new HashMap<>();
  private final boolean isArabic = false;
  private final ArrayList<HashMap<String, Object>> imgsLm = new ArrayList<>();
  private final ArrayList<String> interals = new ArrayList<>();
  public String imgsPath = "", hintsLst = "", json_path = "", code_path = "",project="";
  float scale = 1;
  Path temp_path = new Path();
  Circle sc;
  Path c_path = new Path();
  HashMap<String, Integer> countMap = new HashMap<>();
  TextView targetText;
  private boolean loopDetect = false;
  private ArrayList<String> hintsL = new ArrayList<>();
  private DrawFrame frame;
  private ScrollView vs;
  private LinearLayout linear1;
  private LinearLayout linear;
  private ImageView add;
  private ImageView show;
  private ListView listview1;

  private AlertDialog.Builder dl;
  private SharedPreferences save_sh;
  private AlertDialog cd;

  public VisualScriptingView(Context context, String codePath, String jsonPath, String hnt, String ppath) {
    super(context);
    project = ppath;
    code_path = codePath;
    json_path = jsonPath;
    hintsLst = hnt;
    imgsPath = project+"/images/";
    final androidx.appcompat.app.AlertDialog[] dialog = {null};
    new Handler(Looper.getMainLooper())
        .post(
            new Runnable() {
              @Override
              public void run() {
                dialog[0] = Utils.showMessage(getContext(), "Please wait...", false);
              }
            });
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        Create();
        dialog[0].dismiss();
        new Handler(Looper.getMainLooper())
            .post(
                new Runnable() {
                  @Override
                  public void run() {
                    onDone(VisualScriptingView.this);
                  }
                });
      }
    }.start();
  }

  public VisualScriptingView(Context context, AttributeSet set, int i) {
    super(context, set, i);
  }
  public String getString(int id){
    return getContext().getString(id);
  }
  public void onDone(VisualScriptingView view) {}

  public void setStrings(String... s) {
    code_path = s[0];
    json_path = s[1];
    hintsLst = s[2];
    imgsPath = s[3];
    final androidx.appcompat.app.AlertDialog[] dialog = {null};
    new Handler(Looper.getMainLooper())
        .post(
            new Runnable() {
              @Override
              public void run() {
                dialog[0] = Utils.showMessage(getContext(), "Please wait...", false);
              }
            });
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        Create();
        dialog[0].dismiss();
        new Handler(Looper.getMainLooper())
            .post(
                new Runnable() {
                  @Override
                  public void run() {
                    onDone(VisualScriptingView.this);
                  }
                });
      }
    }.start();
  }

  public void setContentView(int id) {
    removeAllViewsInLayout();
    addView(LayoutInflater.from(getContext()).inflate(id, null));
    setup(getChildAt(0));
  }

  private void setup(View v) {
    if (v != null) {
      if (v instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) v;
        if (v.getId() != R.id.frame) {
          v.setLayoutParams(
              new LinearLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          if (vg.getChildCount() > 0) setup(vg.getChildAt(0));
        }
      }
    }
  }

  public void Create() {
    setContentView(R.layout.visual_scripting);
    initialize();
    initializeLogic();
  }

  private void initialize() {
    frame = findViewById(R.id.frame);
    vs = findViewById(R.id.vs);
    linear1 = findViewById(R.id.linear1);
    linear = findViewById(R.id.linear);
    add = findViewById(R.id.add);
    show = findViewById(R.id.show);
    listview1 = findViewById(R.id.listview1);
    dl = new AlertDialog.Builder(getContext());
    save_sh = getContext().getSharedPreferences("sh", Activity.MODE_PRIVATE);
    setLayoutParams(
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    frame.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View _view) {}
        });

    add.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View _view) {
            /**/
            /*
            final Node node = new Node(getContext()).add(new ValueSetter(getContext())).setAsIf(true);
            frame.addView(node);
            java.util.Timer tm = new java.util.Timer();
            java.util.TimerTask tsk = new TimerTask() {
            	@Override
            	public void run() {
            		new Handler(Looper.getMainLooper()).post((new Runnable() {
            			@Override
            			public void run() {
            				node.updatePos();
            				node.invalidate();
            				frame.invalidate();
            			}
            		});
            	}
            };
            tm.schedule(tsk, (int)(150));
            */
            if (vs.getVisibility() == View.VISIBLE) {
              Runnable runn = () -> vs.setVisibility(View.INVISIBLE);
              vs.animate().scaleX(0).withEndAction(runn).start();
            } else {
              vs.setVisibility(View.VISIBLE);
              vs.animate().scaleX(1).withEndAction(null).start();
            }
          }
        });
    show.setVisibility(View.GONE);
    show.setOnClickListener(_view -> showCode());
  }

  private void initializeLogic() {

    // Setting Up The Variables
    frame.setPath(temp_path);
    frame.setCirclesPath(c_path);
    frame.setOnDragListener(new FrameListener());

    StringBuilder nodes = new StringBuilder();

    // Setting Up The UI
    add.setBackground(
        new GradientDrawable() {
          public GradientDrawable getIns(int a, int b) {
            this.setCornerRadius(a);
            this.setColor(b);
            return this;
          }
        }.getIns(90, 0xFFF44336));

    show.setBackground(
        new GradientDrawable() {
          public GradientDrawable getIns(int a, int b) {
            this.setCornerRadius(a);
            this.setColor(b);
            return this;
          }
        }.getIns(90, 0xFFF44336));

    if (!hintsLst.isEmpty()) {
      hintsL = new Gson().fromJson(hintsLst, new TypeToken<ArrayList<String>>() {}.getType());
    }

    // Loading Nodes Data
    try {
      InputStream nodesIn = getContext().getAssets().open("java/nodes.java");
      int nodesSi = nodesIn.available();
      byte[] nodesBu = new byte[nodesSi];
      nodesIn.read(nodesBu);
      nodesIn.close();
      nodes = new StringBuilder(new String(nodesBu, StandardCharsets.UTF_8));
    } catch (Exception ignored) {
    }

    // loading user Nodes
    ArrayList<String> fls = new ArrayList<>();
    FileUtil.listDir(FileUtil.getPackageDataDir(getContext()).concat("/Nodes/"), fls);
    for (String str1 : fls) {
      String fis = FileUtil.readFile(str1);
      if (!fis.isEmpty()) {
        ArrayList<HashMap<String, Object>> lm =
            new Gson()
                .fromJson(fis, new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
        if (!lm.isEmpty()) nodes.append("\nsplit\n--").append(Uri.parse(str1).getLastPathSegment());
        for (HashMap<String, Object> map : lm) {
          try {
            nodes.append("\nsplit\n").append(map.get("full").toString());
          } catch (Exception e) {

          }
        }
      }
    }

    // Loading Hint For Nodes
    String ss = "";
    try {
      java.io.InputStream ssIn = getContext().getAssets().open("java/hints.java");
      int ssSi = ssIn.available();
      byte[] ssBu = new byte[ssSi];
      ssIn.read(ssBu);
      ssIn.close();
      ss = new String(ssBu, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
    }

    Collections.addAll(hintsL, ss.split("\n"));

    while (nodes.toString().contains("\n\n"))
      nodes = new StringBuilder(nodes.toString().replace("\n\n", "\n"));

    for (final String nds : nodes.toString().split("\nsplit\n")) {
      int[] color = new int[1];
      String[] spT = null;
      if (!(nds.startsWith("--"))) {
        spT = nds.split("\n<<=>>\n");
      } else spT = nds.split(" ");
      final String[] sp = spT;
      final String[] split = sp[0].split(" ");
      if (split[0].startsWith("-color:")) {
        String info = split[0];
        String c = info.substring(0, info.indexOf("•") + 1);
        split[0] = split[0].replace(c, "");
        split[0] = split[0].replace("\n\n", "\n");
        split[0] = split[0].replace("\n", "");
        // ((ClipboardManager)
        // getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", split[0]));
        c = c.replace("-color:", "");
        c = c.replace("•", "");
        color[0] = Color.parseColor(c);
      }
      if (!(nds.startsWith("--"))) {
        try {
          codesMap.put(split[0].replace("__star__if__", ""), sp[1]);
        } catch (Exception ignored) {
        }
      }
      LayoutInflater linearLL = LayoutInflater.from(getContext());
      View linearVV = linearLL.inflate(R.layout.nodes_list_csv, null);
      linear.addView(linearVV);
      final LinearLayout lin = linearVV.findViewById(R.id.lin);
      final TextView text = linearVV.findViewById(R.id.text);
      final ImageView img = linearVV.findViewById(R.id.img);
      linearVV.setLayoutParams(
          new LinearLayout.LayoutParams(
              (ViewGroup.LayoutParams.MATCH_PARENT), (ViewGroup.LayoutParams.WRAP_CONTENT)));
      if (split[0].startsWith("--")) {
        text.setText(split[0].replace("--", ""));
        lin.setBackgroundColor(0xFF202020);
        img.setImageResource(R.drawable.right_icon);
        linearVV.setOnClickListener(
            _view -> {
              for (int i = ((((ViewGroup) _view.getParent()).indexOfChild(_view) + 1));
                  i < linear.getChildCount();
                  i++) {
                View v = linear.getChildAt(i);
                if (v.getTag().toString().contains("sec")) {
                  break;
                }
                if (v.getVisibility() == View.VISIBLE) {
                  v.setVisibility(View.GONE);
                } else {
                  v.setVisibility(View.VISIBLE);
                }
              }
            });
        linearVV.setTag("sec");
      } else {
        text.setText(split[0].replace("__star__if__", ""));
        linearVV.setVisibility(View.GONE);
        linearVV.setOnClickListener(
            _view -> {
              final Node nd = new Node(getContext());
              nd.code = sp[1];
              frame.addView(nd);

              java.util.Timer tm = new java.util.Timer();
              TimerTask tsk =
                  new TimerTask() {
                    @Override
                    public void run() {
                      new Handler(Looper.getMainLooper())
                          .post(
                              () -> {
                                nd.updatePos();
                                nd.update();
                                nd.invalidate();
                                frame.invalidate();
                              });
                    }
                  };
              tm.schedule(tsk, 150);
              nd.setText(split[0].replace("__star__if__", ""));
              if (split[0].contains("__star__if__")) {
                nd.setAsIf(true);
              }
              for (int x = 1; x < split.length; x++) {
                ValueSetter nf = new ValueSetter(getContext());
                nf.setName(split[x]);
                nf.setValue("");
                nf.node = nd;
                nd.setNodeHeaderColor(color[0]);
                nd.add(nf);
              }
            });
        linearVV.setTag("node");
      }
    }
    ArrayList<HashMap<String, Object>> lm = new ArrayList<>();
    if (!FileUtil.readFile(json_path).equals("")) {
      lm =
          new Gson()
              .fromJson(
                  FileUtil.readFile(json_path),
                  new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
      for (HashMap<String, Object> hash : lm) {
        Node node = new Node(getContext());
        frame.addView(node);
        node.loadFrom(hash);
      }
    }
    for (int x = 0; x < frame.getChildCount(); x++) {
      View view = frame.getChildAt(x);
      if (view instanceof Node) {
        Node node = (Node) view;
        node.updatePos();
        try {
          double nx = _toNumber(node.next_id);
          node.next = findViewById((int) nx);
        } catch (Exception ignored) {

        }
        if (node.is_else) {
          try {
            double nx = _toNumber(node.if_id);
            node.true_node = findViewById((int) nx);
          } catch (Exception ignored) {

          }
          try {
            double nx = _toNumber(node.else_id);
            node.false_node = findViewById((int) nx);
          } catch (Exception ignored) {

          }
        }
      }
    }
    if (lm.isEmpty()) {
      // String first=(getIntent().hasExtra("title")) ? getIntent().getStringExtra("title"): " Start
      // ";
      Node node = new Node(getContext());
      node.code = "%1$s";
      node.setText("First > ");
      node.setNodeHeaderColor(Color.parseColor("#574B81"));
      node.disableDel();
      frame.addView(node);
    } else {
      java.util.Timer tm = new java.util.Timer();
      java.util.TimerTask tsk =
          new TimerTask() {
            @Override
            public void run() {
              new Handler(Looper.getMainLooper())
                  .post(
                      new Runnable() {
                        @Override
                        public void run() {
                          for (int i = 0; i < frame.getChildCount(); i++) {
                            View view = frame.getChildAt(i);
                            if (view instanceof Node) {
                              ((Node) view).updatePos();
                              ((Node) view).update();
                              view.invalidate();
                            }
                          }
                          frame.invalidate();
                        }
                      });
            }
          };
      tm.schedule(tsk, 150);
    }
    frame.mScaleGestureDetector =
        new ScaleGestureDetector(
            getContext(),
            new ScaleGestureDetector.OnScaleGestureListener() {
              boolean scaleStart = false;

              @Override
              public void onScaleEnd(ScaleGestureDetector detector) {
                scaleStart = false;
                for (int i = 0; i < frame.getChildCount(); i++) {
                  View view = frame.getChildAt(i);
                  if (view instanceof Node) {
                    ((Node) view).updatePos();
                    ((Node) view).update();
                  }
                }
                frame.invalidate();
              }

              @Override
              public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
              }

              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                if (scaleStart) {
                  if ((scale * scaleFactor < 0.5f) || (scale * scaleFactor > 2)) {
                    return true;
                  }
                  scale *= scaleFactor;
                  frame.scale_value = scale;
                  float wd = frame.getMoveX();
                  float hg = frame.getMoveY();
                  for (int i = 0; i < frame.getChildCount(); i++) {
                    View view = frame.getChildAt(i);
                    if (view instanceof Node) {
                      float x = view.getX();
                      float y = view.getY();
                      view.setX((x - wd) * scaleFactor + wd);
                      view.setY((y - hg) * scaleFactor + hg);
                      ((Node) view).updatePos();
                      ((Node) view).update();
                    }
                  }
                  frame.invalidate();
                } else scaleStart = true;
                return true;
              }
            });
    ((ViewGroup) listview1.getParent()).removeView(listview1);
  }

  public Context getApplicationContext() {
    return getContext();
  }

  public LayoutInflater getLayoutInflater() {
    return LayoutInflater.from(getContext());
  }

  public void showCode() {
    ArrayList<HashMap<String, Object>> lm = new ArrayList<>();
    int id = 0;
    int firstId = 0;
    loopDetect = false;
    String code = "";
    Node p = null;
    for (int i = 0; i < frame.getChildCount(); i++) {
      View v = frame.getChildAt(i);
      if (v instanceof Node) {
        v.setId(id);
        id++;
      }
    }
    for (int i = 0; i < frame.getChildCount(); i++) {
      View v = frame.getChildAt(i);
      if (v instanceof Node) {
        lm.add(((Node) v).getMap());
        if ((((Node) v).left_circle.getParent() == null)) {
          firstId = v.getId();
          p = ((Node) (v));
          p.code = "%1$s";
        }
      }
    }

    code = p.getCode();
    if (!loopDetect) {
      dl = new AlertDialog.Builder(getContext());
      final String result = code;
      dl.setTitle("Code Result");
      dl.setMessage(code);
      dl.setPositiveButton(
          "Copy",
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface _dialog, int _which) {
              ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("clipboard", result));
            }
          });
      dl.setNegativeButton(
          "Cancel",
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface _dialog, int _which) {}
          });
      AlertDialog cd = dl.create();
      Utils.hideSystemUi(cd.getWindow());
      cd.show();
    }
  }

  public void save() {
    /*
    dl = new AlertDialog.Builder(getContext());
    countMap = new HashMap<>();
    dl.setTitle("Save ?");
    dl.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    	@Override
    	public void onClick(DialogInterface _dialog, int _which) {
    		//code
    	}
    });
    dl.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	@Override
    	public void onClick(DialogInterface _dialog, int _which) {
    		//finish();
    	}
    });
    dl.create().show();
    */
    ArrayList<HashMap<String, Object>> lm = new ArrayList<>();
    int id = 0;
    int firstId = 0;
    loopDetect = false;
    String code = "";
    Node p = null;
    for (int i = 0; i < frame.getChildCount(); i++) {
      View v = frame.getChildAt(i);
      if (v instanceof Node) {
        v.setId(id);
        id++;
      }
    }
    for (int i = 0; i < frame.getChildCount(); i++) {
      View v = frame.getChildAt(i);
      if (v instanceof Node) {
        lm.add(((Node) v).getMap());
        if ((((Node) v).left_circle.getParent() == null)) {
          firstId = v.getId();
          p = ((Node) (v));
          p.code = "%1$s";
        }
      }
    }
    code = p.getCode();
    if (!loopDetect) {
      FileUtil.writeFile(json_path, new Gson().toJson(lm));
      FileUtil.writeFile(code_path, code);
      // finish();
    }
  }

  public void _DragAble(final View _view) {
    /**/
    /*
    _view.setOnTouchListener(new OnTouchListener() {
    	PointF DownPT = new PointF();
    	PointF StartPT = new PointF();
    	boolean stop=false;
    	@Override public boolean onTouch(View v, MotionEvent event) {
    		if(!stop){
    			switch (event.getAction()) {
    				case MotionEvent.ACTION_MOVE:PointF mv = new PointF(event.getX() - DownPT.x, event.getY() - DownPT.y);
    				_view.setX((int)(StartPT.x+mv.x));
    				_view.setY((int)(StartPT.y+mv.y));
    				StartPT = new PointF(_view.getX(), _view.getY());
    				break; //xenon
    				case MotionEvent.ACTION_DOWN : DownPT.x = event.getX();
    				DownPT.y = event.getY();
    				StartPT = new PointF(_view.getX(), _view.getY());
    				break;
    				case MotionEvent.ACTION_UP : break;
    				default : break;
    			}
    		}

    		if(event.getEventTime()<=500){
    			float x=DownPT.x-event.getX(), y=DownPT.y-event.getY();
    			float dist = x*x-y*y;
    			if(dist<=v.getMeasuredWidth()/4){
    				stop = true;
    				_popupFor((Node)v);
    			}
    		}

    		//_view.invalidate();
    		for (int i = 0;i<frame.getChildCount();i++){
    			View view = frame.getChildAt(i);
    			try {
    				((Node)view).updatePos();
    			} catch(Exception ex){}
    		}
    		frame.invalidate();
    		return false;
    }});

    */
    _view.setOnTouchListener(
        new OnTouchListener() {
          final PointF DownPT = new PointF();
          final PointF DownPTR = new PointF();
          PointF StartPT = new PointF();

          @Override
          public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
              case MotionEvent.ACTION_MOVE:
                PointF mv = new PointF(event.getX() - DownPT.x, event.getY() - DownPT.y);
                _view.setX((int) (StartPT.x + mv.x));
                _view.setY((int) (StartPT.y + mv.y));
                StartPT = new PointF(_view.getX(), _view.getY());
                break; // xenon
              case MotionEvent.ACTION_DOWN:
                DownPT.x = event.getX();
                DownPT.y = event.getY();
                DownPTR.x = event.getRawX();
                DownPTR.y = event.getRawY();
                StartPT = new PointF(_view.getX(), _view.getY());
                break;
              case MotionEvent.ACTION_UP:
                float x = DownPTR.x - event.getRawX(), y = DownPTR.y - event.getRawY();
                float dist = (float) Math.sqrt(x * x + y * y);
                if (dist <= Utils.convertPixelsToDp(getApplicationContext(), 20)) {
                  // Utils.showMessage(getApplicationContext(), "d : "+dist+" , x : "+x+", y : "+y);
                  if (((Node) _view).left_circle.getVisibility() == View.VISIBLE)
                    _popupFor((Node) v);
                }
                break;
              default:
                break;
            }

            // _view.invalidate();
            for (int i = 0; i < frame.getChildCount(); i++) {
              View view = frame.getChildAt(i);
              try {
                ((Node) view).updatePos();
              } catch (Exception ex) {
              }
            }
            frame.invalidate();
            return false;
          }
        });
    /* Made by XenonDry!!! */
    _view.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View _view) {}
        });
  }

  public void _setHW(final View _view, final double _width, final double _height) {
    if (_view.getLayoutParams() != null) {
      _view.getLayoutParams().width = ((int) (_width));
      _view.getLayoutParams().height = ((int) (_height));
    } else {
      _view.setLayoutParams(new FrameLayout.LayoutParams((int) (_width), (int) (_height)));
    }
  }
  
  public void deleteNode(Node _node){
      frame.removeView(_node);
      frame.removeView(_node.right_circle);
      frame.removeView(_node.left_circle);
      frame.removeView(_node.true_circle);
      frame.removeView(_node.false_circle);
      _node.cpath.reset();
      _node.path.reset();
      for (int i = 0; i < frame.getChildCount(); i++) {
        View view = frame.getChildAt(i);
        if (view instanceof Node) {
          ((Node) view).updatePos();
          view.invalidate();
        }
      }
      frame.invalidate();
  }
  
  public void copyNode(Node _node){
      final Node nd = new Node(getContext());
      nd.loadFrom(_node.getMap());
      frame.addView(nd);
      java.util.Timer tm = new java.util.Timer();
      java.util.TimerTask tsk =
          new TimerTask() {
            @Override
            public void run() {
              new Handler(Looper.getMainLooper())
                  .post(
                      new Runnable() {
                        @Override
                        public void run() {
                          nd.update();
                          nd.updatePos();
                          nd.invalidate();
                          frame.invalidate();
                        }
                      });
            }
          };
      tm.schedule(tsk, (150));
  }
  
  public void _popupFor(final Node _node){
        PopupMenu popupMenu = new PopupMenu(getContext(), _node);
        popupMenu.getMenu().add(0, 0,0,getString(R.string.delete)).setIcon(R.drawable.ic_delete);
        popupMenu.getMenu().add(0,1,1,getString(R.string.copy)).setIcon(R.drawable.copy_icon);
    
        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if(item.getItemId()==1)
                            copyNode(_node);
                                else deleteNode(_node);
                        return true;
                    }
                });
        popupMenu.show();
  }

  public double _toNumber(final String s) {
    String str = replaceNonstandardDigits(s);
    try {
      return Double.parseDouble(str);
    } catch (Exception ex) {
      String err1 = Log.getStackTraceString(ex);
      try {
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        return nf.parse(str).doubleValue();
      } catch (java.text.ParseException e) {
        throw new RuntimeException(err1 + "\n" + Log.getStackTraceString(e));
        // return 0;
      }
    }
  }

  public float _toNumberf(String s) {
    String str = replaceNonstandardDigits(s);
    try {
      return Float.parseFloat(str);
    } catch (Exception ex) {
      String err1 = Log.getStackTraceString(ex);
      try {
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        return nf.parse(str).floatValue();
      } catch (java.text.ParseException e) {
        throw new RuntimeException(err1 + "\n" + Log.getStackTraceString(e));
      }
    }
  }

  public String replaceNonstandardDigits(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      if (isNonstandardDigit(ch)) {
        int numericValue = Character.getNumericValue(ch);
        if (numericValue >= 0) {
          builder.append(numericValue);
        }
      } else {
        builder.append(ch);
      }
    }
    return builder.toString().replace(",", ".").replace("٫", ".");
  }

  private boolean isNonstandardDigit(char ch) {
    return Character.isDigit(ch) && !(ch >= '0' && ch <= '9');
  }

  public void _infiniteLoop() {
    if (!loopDetect) {
      dl = new AlertDialog.Builder(getContext());
      loopDetect = true;
      dl.setTitle("Error");
      dl.setMessage("There is infinite Loop, please fix it...!!  (🔁)");
      dl.setCancelable(false);
      dl.setPositiveButton(
          "OK",
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface _dialog, int _which) {
              loopDetect = false;
            }
          });
      dl.create().show();
    }
  }
  String[] imagesList = new String[]{};
  public String[] getImagesList(){
    return imagesList;
  }
  
  public void _refreshList() {
    ArrayList<String> arr = new ArrayList<>();
    StringBuilder p = new StringBuilder();
    for (String s : interals) p.append("/").append(s);
    FileUtil.listDir(imgsPath.concat(p.toString()), arr);
    imgsLm.clear();
    if (!interals.isEmpty()) {
      {
        HashMap<String, Object> _item = new HashMap<>();
        _item.put("file", "...");
        imgsLm.add(_item);
      }
    }
    imagesList = new String[arr.size()];
    int xx =0;
    for (String s : arr) {
        HashMap<String, Object> _item = new HashMap<>();
        _item.put("file", s);
        imgsLm.add(_item);
        imagesList[xx] = s;
        xx++;
    }
    listview1.setAdapter(new Listview1Adapter(imgsLm));
  }

  public String _remove_last_from_path(final String _path) {
    String st = "";
    ArrayList<String> lsts = new ArrayList<>();
    try {
      String[] lines = _path.concat("/").split("/");
      Collections.addAll(lsts, lines);
    } catch (Exception e8727) {
    }
    lsts.remove(lsts.size() - 1);
    for (String hh : lsts) {
      if (FileUtil.isDirectory(hh)) {
        if (st == "") {
          st = hh;
        } else {
          st = st + "/" + hh;
        }
      } else {
        if (st == "") {
          st = hh;
        } else {
          st = st + "/" + hh;
        }
      }
    }
    return (st);
  }

  public void _setImageFromFile(final ImageView _img, final String _path) {
    java.io.File file = new java.io.File(_path);
    Uri imageUri = Uri.fromFile(file);

    Glide.with(getApplicationContext()).asBitmap().load(imageUri).into(_img);
    /**/
    /*

    class th extends Thread {
    	@Override
    	public void run(){
    		final Bitmap bit = FileUtil.decodeSampleBitmapFromPath(_path, 1024, 1024);
    		new Handler(Looper.getMainLooper()).post(new Runnable() {
    			@Override
    			public void run() {
    				_img.setImageBitmap(bit);
    			}
    		});
    	}
    }
    new th().start();
    */
  }

  class Node extends LinearLayout {
    private final TextView false_text;
    private final TextView true_text;
    private final TextView title;
    private final LinearLayout container;
    private final LinearLayout parent;
    private final Circle left_circle;
    private final Circle right_circle;
    private final Circle true_circle;
    private final Circle false_circle;
    public Node next, true_node, false_node;
    public Path path = new Path(), cpath = new Path();
    public boolean is_else = false;
    String code = "", else_id = "", next_id = "", if_id = "";
    int comes = 0;
    private int color = Color.parseColor("#0078fe");

    public Node(Context ctx) {
      super(ctx);

      // Setting Up The Node View
      LayoutInflater thisLL = LayoutInflater.from(getContext());
      View thisVV = thisLL.inflate(R.layout.node, null);
      this.addView(thisVV);

      // Init Views
      title = thisVV.findViewById(R.id.title);
      parent = thisVV.findViewById(R.id.parent);
      container = thisVV.findViewById(R.id.container);
      true_text = thisVV.findViewById(R.id.true_text);
      false_text = thisVV.findViewById(R.id.false_text);

      // Setting Up The UI Changes
      GradientDrawable i1 =
          new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[] {color, color});
      i1.setCornerRadii(
          new float[] {
            (float) (10),
            (float) (10),
            (float) (10),
            (float) (10),
            (float) (0),
            (float) (0),
            (float) (0),
            (float) (0)
          }); // LeftTop, //RightTop, //RightBottom, //LeftBottom,
      title.setBackground(i1);

      GradientDrawable id =
          new GradientDrawable(
              GradientDrawable.Orientation.RIGHT_LEFT, new int[] {0xFF3C3C3C, 0xFF3C3C3C});
      id.setCornerRadii(
          new float[] {
            (10), (10), (10), (10), (10), (10), (10), (10)
          }); // LeftTop, //RightTop, //RightBottom, //LeftBottom,
      parent.setBackground(id);

      this.setLayoutParams(
          new LinearLayout.LayoutParams(
              (ViewGroup.LayoutParams.WRAP_CONTENT), (ViewGroup.LayoutParams.WRAP_CONTENT)));

      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          false_text, (1), (100000), (1), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          true_text, (1), (100000), (1), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          title, (1), (100000), (1), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);

      setX(0);
      setY(0);

      right_circle = new Circle(getContext());
      left_circle = new Circle(getContext());
      true_circle = new Circle(getContext());
      false_circle = new Circle(getContext());

      frame.addView(right_circle);
      frame.addView(left_circle);
      frame.addView(false_circle);
      frame.addView(true_circle);

      right_circle.node = this;
      left_circle.node = this;
      true_circle.node = this;
      false_circle.node = this;

      left_circle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

      update();

      frame.add(path);
      frame.addToC(cpath);

      _DragAble(this);

      right_circle.setOnTouchListener(
          (v, event) -> {
            int ev = event.getAction();
            switch (ev) {
              case MotionEvent.ACTION_DOWN:
                left_circle.setOnDragListener(null);
                sc = (Circle) v;
                sc.setTag(null);
                sc.node.next = null;
                frame.invalidate();
                ClipData data = ClipData.newPlainText("", "");
                // add drag shadow
                DragShadowBuilder shadow = new DragShadowBuilder(v);
                if (Build.VERSION.SDK_INT >= 24) {
                  v.startDragAndDrop(data, shadow, v, 1);
                } else {
                  v.startDrag(data, shadow, v, 1);
                }

                break;
            }
            return true;
          });

      true_circle.setOnTouchListener(
          (v, event) -> {
            int ev = event.getAction();
            if (ev == MotionEvent.ACTION_DOWN) {
              sc = (Circle) v;
              sc.setTag("true");
              sc.node.true_node = null;
              frame.invalidate();
              ClipData data = ClipData.newPlainText("", "");
              // add drag shadow
              DragShadowBuilder shadow = new DragShadowBuilder(v);
              if (Build.VERSION.SDK_INT >= 24) {
                v.startDragAndDrop(data, shadow, v, 1);
              } else {
                v.startDrag(data, shadow, v, 1);
              }
            }
            return true;
          });
      false_circle.setOnTouchListener(
          (v, event) -> {
            int ev = event.getAction();
            if (ev == MotionEvent.ACTION_DOWN) {
              sc = (Circle) v;
              sc.setTag("false");
              sc.node.false_node = null;
              frame.invalidate();
              ClipData data = ClipData.newPlainText("", "");
              // add drag shadow
              DragShadowBuilder shadow = new DragShadowBuilder(v);
              if (Build.VERSION.SDK_INT >= 24) {
                v.startDragAndDrop(data, shadow, v, 1);
              } else {
                v.startDrag(data, shadow, v, 1);
              }
            }
            return true;
          });
      left_circle.setOnDragListener(new CircleListener());
    }

    public void setId(int id) {
      super.setId(id);
      comes = 0;
    }

    public void setText(String s) {
      title.setText(s);
    }

    public void disableDel() {
      frame.removeView(left_circle);
    }

    public void updatePos() {
      try {
        true_circle.setVisibility(false_text.getVisibility());
        false_circle.setVisibility(false_text.getVisibility());
        float space = Utils.convertPixelsToDp(getContext(), 6) * scale;
        float r = (getX() + getMeasuredWidth() + space);
        float l = (getX() - right_circle.getMeasuredWidth() - space);

        right_circle.setX(r);
        right_circle.setY(getY() + space);

        left_circle.setX(l);
        left_circle.setY(getY() + space);

        true_circle.setX(r);
        true_circle.setY(getY() + true_text.getY() + space);
        false_circle.setX(r);
        false_circle.setY(getY() + false_text.getY() + space);
        path.reset();
        cpath.reset();
        float rx = getRX(),
            ry = getRY(),
            lx = getLX(),
            ly = getLY(),
            tx = getTX(),
            ty = getTY(),
            fx = getFX(),
            fy = getFY();

        if (next != null) {
          if (next.getParent() == null) {
            next = null;
          } else {
            float nrx = next.getRX(), nry = next.getRY(), nlx = next.getLX(), nly = next.getLY();
            path.moveTo(rx, ry);
            path.cubicTo(rx + 100, ry, nlx - 100, nly, nlx, nly);
            cpath.addCircle(rx, ry, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
            cpath.addCircle(nlx, nly, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
          }
        }

        if (true_node != null) {
          if (true_node.getParent() == null) {
            true_node = null;
          } else {
            float ntx = true_node.getTX(),
                nty = true_node.getTY(),
                nfx = true_node.getFX(),
                nfy = true_node.getFY(),
                nx = true_node.getLX(),
                ny = true_node.getLY();
            path.moveTo(tx, ty);
            path.cubicTo(tx + 100, ty, nx - 100, ny, nx, ny);
            cpath.addCircle(tx, ty, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
            cpath.addCircle(nx, ny, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
          }
        }

        if (false_node != null) {
          if (false_node.getParent() == null) {
            false_node = null;
          } else {
            float ntx = false_node.getTX(),
                nty = false_node.getTY(),
                nfx = false_node.getFX(),
                nfy = false_node.getFY(),
                nx = false_node.getLX(),
                ny = false_node.getLY();
            path.moveTo(fx, fy);
            path.cubicTo(fx + 100, fy, nx - 100, ny, nx, ny);
            cpath.addCircle(fx, fy, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
            cpath.addCircle(nx, ny, right_circle.getMeasuredHeight() / 2, Path.Direction.CW);
          }
        }
      } catch (Exception exx) {
      }
    }

    public void setX(float x) {
      super.setX(x);
      updatePos();
    }

    public void setY(float y) {
      super.setY(y);
      updatePos();
    }

    public void setNodeHeaderColor(int color) {
      if (color != 0) {
        this.color = color;
        android.graphics.drawable.GradientDrawable i1 =
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                new int[] {color, color});
        i1.setCornerRadii(
            new float[] {
              (float) (10),
              (float) (10),
              (float) (10),
              (float) (10),
              (float) (0),
              (float) (0),
              (float) (0),
              (float) (0)
            }); // LeftTop, //RightTop, //RightBottom, //LeftBottom,
        title.setBackground(i1);
      }
    }

    /**/
    /*
    FileUtil.writeFile(FileUtil.getPackageDataDir(getApplicationContext()).concat("/log.txt"), "r : "+r+"\n l : "+l+"\n tx : "+tx+"\n ty : "+ty);
    */
    protected void onDraw(Canvas cv) {
      super.onDraw(cv);
      updatePos();
    }

    protected void onSizeChanged(int n, int n2, int n3, int n4) {
      super.onSizeChanged(n, n2, n3, n4);
      update();
      updatePos();
    }

    public void update() {
      int wd = title.getText().toString().length() * 10;
      int len = (int) (300 * scale);
      for (int x = 0; x < container.getChildCount(); x++) {
        ValueSetter vs = (ValueSetter) (container.getChildAt(x));
        vs.update();
        vs.linear.setMinimumWidth(len);
      }
      false_text.setPadding(
          (0),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), (3)) * scale),
          (0),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), 3) * scale));
      container.setPadding(
          (int) (Utils.convertPixelsToDp(getApplicationContext(), (8)) * scale),
          (Utils.convertPixelsToDp(getApplicationContext(), (int) (8 * scale))),
          (Utils.convertPixelsToDp(getApplicationContext(), (int) (8 * scale))),
          (Utils.convertPixelsToDp(getApplicationContext(), (int) (8 * scale))));
      _setHW(
          false_text,
          android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
          Utils.convertPixelsToDp(getApplicationContext(), 25) * scale);
      _setHW(
          true_text,
          android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
          Utils.convertPixelsToDp(getApplicationContext(), 25) * scale);
      true_text.setPadding(
          (0),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), (3)) * scale),
          (0),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), 3) * scale));
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              (int) (Utils.convertPixelsToDp(getContext(), 35) * scale));
      int top = 0;
      int bottom = (int) (Utils.convertPixelsToDp(getApplicationContext(), 10) * scale);
      int right = 0;
      int left = 0;
      params.setMargins(left, top, right, bottom);
      title.setLayoutParams(params);
      title.setPadding(
          Utils.convertPixelsToDp(getApplicationContext(), (int) (8 * scale)),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), 8) * scale),
          (int) (Utils.convertPixelsToDp(getApplicationContext(), 8) * scale),
          (Utils.convertPixelsToDp(getApplicationContext(), (int) (8 * scale))));
      parent.setMinimumWidth(len);
      if (left_circle != null) {
        left_circle.update();
        right_circle.update();
        true_circle.update();
        false_circle.update();
      }
    }

    Node add(ValueSetter setter) {
      container.addView(setter);
      return this;
    }

    public Node setAsIf(boolean b) {
      int v = (b ? View.VISIBLE : View.GONE);
      true_text.setVisibility(v);
      false_text.setVisibility(v);
      is_else = b;
      return this;
    }

    float getLX() {
      // left circle x

      float xtc = (left_circle.circle.getX() + (left_circle.circle.getMeasuredWidth() / 2));

      float xt = (left_circle.getX() + left_circle.getMeasuredWidth() - xtc);

      return xt;
    }

    float getRX() {
      return (right_circle.getX() + (right_circle.circle.getMeasuredWidth() / 2));
    }

    float getLY() {
      float ytc = (left_circle.circle.getY() + (left_circle.circle.getMeasuredHeight() / 2));

      float yt = (left_circle.getY() + (left_circle.getMeasuredHeight() / 2));
      return yt;
    }

    float getRY() {

      float ytc = (right_circle.circle.getY() + (right_circle.circle.getMeasuredHeight() / 2));

      return (right_circle.getY() + (right_circle.circle.getMeasuredWidth() / 2));
    }

    float getTX() {
      return (true_circle.getX() + (true_circle.circle.getMeasuredWidth() / 2));
    }

    float getFX() {
      return (false_circle.getX() + (false_circle.circle.getMeasuredWidth() / 2));
    }

    float getTY() {
      return (true_circle.getY() + (true_circle.circle.getMeasuredHeight() / 2));
    }

    float getFY() {
      return (false_circle.getY() + (false_circle.circle.getMeasuredHeight() / 2));
    }

    public void loadFrom(HashMap<String, Object> map) {
      if (map.containsKey("color")) {
        this.setNodeHeaderColor((int) Float.parseFloat(map.get("color").toString()));
      }
      this.setText(map.get("title").toString());
      try {
        if (codesMap.containsKey(map.get("title").toString())) {
          this.code = codesMap.get(map.get("title").toString()).toString();
        } else this.code = map.get("code").toString();
      } catch (Exception ignored) {
      }
      try {
        this.setAsIf(map.get("else").toString().equals("true"));
      } catch (Exception ignored) {
      }
      this.setX((float) (_toNumber(map.get("x").toString())) * scale);
      this.setY((float) (_toNumber(map.get("y").toString())) * scale);
      float xx = this.getX() * this.getX();
      float yy = this.getY() * this.getY();
      try {
        if (this.is_else) {
          next_id = map.get("next_id").toString();
        } else next_id = map.get("next").toString();
      } catch (Exception ex) {
      }
      try {
        else_id = map.get("else_id").toString();
      } catch (Exception ex) {
      }
      try {
        if_id = map.get("next").toString();
      } catch (Exception ex) {
      }
      this.setId((int) (_toNumber(map.get("id").toString())));
      if (map.get("nf") == null) return;
      try {
        if (!(map.get("close").toString().contains("t"))) {
          disableDel();
        }
      } catch (Exception ex) {
      }
      ArrayList<HashMap<String, Object>> lm =
          new Gson()
              .fromJson(
                  map.get("nf").toString(),
                  new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
      for (HashMap<String, Object> hash : lm) {
        ValueSetter nf = new ValueSetter(getContext());
        nf.node = this;
        try {
          nf.setName(hash.get("name").toString());
        } catch (Exception ex) {
        }
        try {
          nf.setValue(hash.get("value").toString());
        } catch (Exception ex) {
        }
        this.container.addView(nf);
      }
    }

    public HashMap<String, Object> getMap() {
      HashMap<String, Object> map = new HashMap<>();
      map.put("close", (left_circle.getParent() != null) ? "true" : "false");
      map.put("title", title.getText().toString());
      map.put("code", this.code);
      map.put("color", this.color);
      if (true_text.getVisibility() == View.VISIBLE) {
        map.put("next", ((true_node == null) ? "" : true_node.getId() + ""));
      } else {
        map.put("next", (this.next == null) ? "null" : this.next.getId() + "");
      }
      map.put("else", true_text.getVisibility() == View.VISIBLE ? "true" : "false");
      map.put("else_id", ((false_node == null) ? "" : false_node.getId() + ""));
      map.put(
          "next_id",
          true_text.getVisibility() == View.VISIBLE
              ? ((next == null) ? "" : next.getId() + "")
              : "null");
      map.put("x", String.valueOf(this.getX() * 1 / scale));
      map.put("y", String.valueOf(this.getY() * 1 / scale));
      map.put("id", this.getId() + "");
      ArrayList<HashMap<String, Object>> array = new ArrayList<>();
      for (int i = 0; i < container.getChildCount(); i++) {
        View v = container.getChildAt(i);
        if (v instanceof ValueSetter) {
          array.add(((ValueSetter) v).getMap());
        }
      }
      map.put("nf", new Gson().toJson(array));
      return map;
    }

    public String getCode() {
      if (loopDetect) return "";
      String id = getId() + "";
      // countMap.put(id,(countMap.containsKey(id))?countMap.get(id):0);
      comes++;
      if (comes > frame.getChildCount() * 10) {
        _infiniteLoop();
        return "";
      }
      ArrayList<String> ls = new ArrayList<>();
      for (int i = 0; i < container.getChildCount(); i++) {
        View v = container.getChildAt(i);
        if (v instanceof ValueSetter) {
          ls.add(((ValueSetter) v).getValue());
        }
      }
      if (this.is_else) {
        if (this.true_node == null) ls.add("");
        else ls.add(true_node.getCode());
        if (this.false_node == null) ls.add("");
        else ls.add(false_node.getCode());
      }
      if (this.next == null) ls.add("");
      else ls.add(next.getCode());

      String[] s = new String[ls.size()];
      for (int x = 0; x < ls.size(); x++) {
        s[x] = ls.get(x);
      }

      String c = "";
      try {
        c = c + String.format(this.code, (Object[]) s);
      } catch (Exception ex) {
      }
      return c;
    }
  }

  class ValueSetter extends LinearLayout {
    private final TextView value;
    private final TextView name;
    private String type="";
    private String mainName;    
    public LinearLayout linear;
    public Node node;

    public ValueSetter(Context ctx) {
      super(ctx);

      // Init Node Input View
      LayoutInflater thisLL = getLayoutInflater();
      View thisVV = thisLL.inflate(R.layout.value_setter, null);
      this.addView(thisVV);

      // Load Views
      value = thisVV.findViewById(R.id.value);
      linear = thisVV.findViewById(R.id.linear);
      name = thisVV.findViewById(R.id.name);

      // Setting Up UI
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          value, (1), (100000), (1), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          name, (1), (100000), (1), TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);

      update();

      this.setBackground(
          new GradientDrawable() {
            public GradientDrawable getIns(int a, int b) {
              this.setCornerRadius(a);
              this.setColor(b);
              return this;
            }
          }.getIns(10, Color.TRANSPARENT));
      this.linear.setBackground(
          new GradientDrawable() {
            public GradientDrawable getIns(int a, int b) {
              this.setCornerRadius(a);
              this.setColor(b);
              return this;
            }
          }.getIns(10, 0xFF1E1E1E));
      value.setBackground(
          new GradientDrawable() {
            public GradientDrawable getIns(int a, int b) {
              this.setCornerRadius(a);
              this.setColor(b);
              return this;
            }
          }.getIns(10, 0xFFCFD8DC));
      value.setOnTouchListener(
          new OnTouchListener() {
            final PointF DownPT = new PointF();
            final PointF DownPTR = new PointF();
            PointF StartPT = new PointF();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
              View _view = node;
              switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                  PointF mv = new PointF(event.getX() - DownPT.x, event.getY() - DownPT.y);
                  _view.setX((int) (StartPT.x + mv.x));
                  _view.setY((int) (StartPT.y + mv.y));
                  StartPT = new PointF(_view.getX(), _view.getY());
                  break; // xenon
                case MotionEvent.ACTION_DOWN:
                  DownPT.x = event.getX();
                  DownPT.y = event.getY();
                  DownPTR.x = event.getRawX();
                  DownPTR.y = event.getRawY();
                  StartPT = new PointF(_view.getX(), _view.getY());
                  break;
                case MotionEvent.ACTION_UP:
                  float x = DownPTR.x - event.getRawX(), y = DownPTR.y - event.getRawY();
                  float dist = (float) Math.sqrt(x * x + y * y);
                  if (dist <= Utils.convertPixelsToDp(getApplicationContext(), (20))) {
                    if (type != null && type != "") {
                      String[] menuItems=provideListForType(type);
                      if(menuItems==null) {
                      edit();
                        return true;
                      }
                      showPopupMenu(value, menuItems);
                    } else edit();
                  }
                  break;
                default:
                  break;
              }

              // _view.invalidate();
              for (int i = 0; i < frame.getChildCount(); i++) {
                View view = frame.getChildAt(i);
                try {
                  ((Node) view).updatePos();
                } catch (Exception ex) {
                }
              }
              frame.invalidate();
              return true;
            }
          });

      if (isArabic) {
        linear.removeView(name);
        try {
          ((ViewGroup) name.getParent()).removeView(name);
        } catch (Exception e) {

        }
        linear.addView(name);
      }
    }

    public String getValue() {
      return value.getText().toString();
    }

    public void setValue(String v) {
      value.setText(v);
    }

    public String getName() {
      return name.getText().toString();
    }

    public void setName(String nm) {
            mainName=nm;
            if(nm.contains("(")){
                int b1=nm.indexOf("(");
                int b2=nm.indexOf(")");
                setType(nm.substring(b1+1,b2));
                name.setText(nm.replace("("+nm.substring(b1+1,b2)+")",""));
            }else
      name.setText(nm);
    }

    public void update() {

      value.setPadding(
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale));
      {
        LayoutParams params =
            new LayoutParams((int) (pxToDp(70) * scale), (int) (pxToDp(25) * scale));
        int top = 0;
        int bottom = 0;
        int right = (int) (pxToDp(8) * scale);
        int left = 0;
        params.setMargins(left, top, right, bottom);

        value.setLayoutParams(params);
      }
      name.setPadding(
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale),
          (int) (pxToDp(3) * scale));

      linear.setPadding(
          (int) (pxToDp(4) * scale),
          (int) (pxToDp(4) * scale),
          (int) (pxToDp(4) * scale),
          (int) (pxToDp(4) * scale));

      int len = name.getText().toString().length() * 15;
      if (len < 100) len = 100;

      name.setLayoutParams(
          new LayoutParams((int) ((scale * pxToDp(len))), (int) (pxToDp(25) * scale)));

      LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      {
        int top = 0;
        int bottom = (int) (pxToDp(2) * scale);
        int right = 0;
        int left = 0;
        params.setMargins(left, top, right, bottom);
      }

      linear.setLayoutParams(params);
    }

    public HashMap<String, Object> getMap() {
      HashMap<String, Object> map = new HashMap<>();
      map.put("name", mainName);
      map.put("value", getValue());
      return map;
    }

    private void showPopupMenu(View v, String[] menuItems) {
        PopupMenu popupMenu = new PopupMenu(getContext(), v);
        
        popupMenu.getMenu().add(0,0,0,getString(R.string.enter_value)).setIcon(R.drawable.edit_icon);
        for (int i = 1; i < menuItems.length+1; i++) {
            popupMenu.getMenu().add(0, i, i, menuItems[i-1]);
        }
    
        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if(item.getItemId()==0){
                            edit();
                        } else {
                           value.setText(menuItems[item.getItemId()-1]);
                           setValue(menuItems[item.getItemId()-1]);
                        }
                        return true;
                    }
                });
        popupMenu.show();
    }


    public void edit() {
      if (getType().equals("image")) {
            interals.clear();
            _refreshList();
            if(imgsLm.size()!=0){
                try {
                  ((ViewGroup) listview1.getParent()).removeView(listview1);
                } catch (Exception e) {
        
                }
                cd =
                    new AlertDialog.Builder(
                            getContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                        .create();
                cd.setView(listview1);
                Utils.hideSystemUi(cd.getWindow());
                cd.show();
                targetText = value;
                return;
          }
      }
      final AlertDialog cd =
          new AlertDialog.Builder(
                  getContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
              .create();
      LayoutInflater cdLI = getLayoutInflater();
      View cdCV = cdLI.inflate(R.layout.values_edit_dialog, null);
      cd.setView(cdCV);
      Utils.hideSystemUi(cd.getWindow());
      final LinearLayout linear = cdCV.findViewById(R.id.linear);
      final LinearLayout hints = cdCV.findViewById(R.id.hints);
      final TextView text = cdCV.findViewById(R.id.text);
      final ImageView hide = cdCV.findViewById(R.id.hide);
      final ImageView del = cdCV.findViewById(R.id.del);
      final ImageView clear = cdCV.findViewById(R.id.clear);
      final ImageView done = cdCV.findViewById(R.id.done);
      final EditText edittext = cdCV.findViewById(R.id.edittext);
      final ImageView edit = cdCV.findViewById(R.id.edit);
      Utils.hideSystemUi(cd.getWindow());
      cd.show();
      text.setText(value.getText().toString());
      edittext.setText(text.getText().toString());
      edittext.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
              final String edittextnn = _param1.toString();

              if (!text.getText().toString().equals(edittext.getText().toString())) {
                text.setText(edittext.getText().toString());
              }
            }

            @Override
            public void beforeTextChanged(
                CharSequence _param1, int _param2, int _param3, int _param4) {}

            @Override
            public void afterTextChanged(Editable _param1) {}
          });
      hide.setOnClickListener(_view -> cd.dismiss());
      done.setOnClickListener(
          _view -> {
            if (edittext.getVisibility() == View.GONE) {
              value.setText(text.getText().toString());
            } else {
              value.setText(edittext.getText().toString());
            }
            cd.dismiss();
          });
      clear.setOnClickListener(
          _view -> {
            text.setText("");
            edittext.setText(text.getText().toString());
          });
      edit.setOnClickListener(
          _view -> {
            edit.setVisibility(View.GONE);
            text.setVisibility(View.GONE);
            edittext.setVisibility(View.VISIBLE);
          });
      del.setOnClickListener(
          _view -> {
            try {
              double nx = text.getText().toString().length() - 1;
              if ("abcdefghijklmnopqrstuvwxyz"
                  .contains(
                      String.valueOf(text.getText().toString().charAt((int) (nx))).toLowerCase())) {
                while ((!text.getText().toString().isEmpty())
                    && "abcdefghijklmnopqrstuvwxyz"
                        .contains(
                            String.valueOf(text.getText().toString().charAt((int) (nx)))
                                .toLowerCase())) {
                  text.setText(
                      text.getText()
                          .toString()
                          .substring((0), (text.getText().toString().length() - 1)));
                  nx = text.getText().toString().length() - 1;
                }
              } else {
                text.setText(
                    text.getText()
                        .toString()
                        .substring((0), (text.getText().toString().length() - 1)));
              }
            } catch (Exception e) {

            }
            edittext.setText(text.getText().toString());
          });

      for (int ix = 0; ix < linear.getChildCount(); ix++) {
        View vix = linear.getChildAt(ix);
        for (int i = 0; i < ((ViewGroup) (vix)).getChildCount(); i++) {
          View v = ((ViewGroup) (vix)).getChildAt(i);
          if (v instanceof TextView) {
            v.setOnClickListener(
                new View.OnClickListener() {
                  @Override
                  public void onClick(View _view) {
                    text.setText(
                        text.getText().toString().concat(((TextView) _view).getText().toString()));
                    edittext.setText(text.getText().toString());
                  }
                });
            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
            params.weight = (1);
            if (i == 0) {
              int top = Utils.convertPixelsToDp(getApplicationContext(), (5));
              int bottom = 0;
              int right = Utils.convertPixelsToDp(getApplicationContext(), (5));
              int left = Utils.convertPixelsToDp(getApplicationContext(), (5));
              params.setMargins(left, top, right, bottom);
            } else {
              int top = Utils.convertPixelsToDp(getApplicationContext(), (5));
              int bottom = 0;
              int right = Utils.convertPixelsToDp(getApplicationContext(), (5));
              int left = 0;
              params.setMargins(left, top, right, bottom);
            }
            v.setLayoutParams(params);
          }
        }
      }
      for (String ss : hintsL) {
        final TextView txt = new TextView(getContext());
        txt.setText(ss);
        txt.setBackground(
            new GradientDrawable() {
              public GradientDrawable getIns(int a, int b, int c, int d) {
                this.setCornerRadius(a);
                this.setStroke(b, c);
                this.setColor(d);
                return this;
              }
            }.getIns(0, 1, 0xFFFFFFFF, 0xFF0078FE));
        txt.setTextColor(0xFFFFFFFF);
        txt.setSingleLine(true);
        txt.setPadding(
            (8),
            Utils.convertPixelsToDp(getApplicationContext(), 10),
            (2),
            (Utils.convertPixelsToDp(getApplicationContext(), 10)));
        if (ss.startsWith("-")) {
          txt.setBackgroundColor(0xFF1A1A1A);
          txt.setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View _view) {
                  int x = ((ViewGroup) txt.getParent()).indexOfChild(txt) + 1;
                  for (int i = x; i < hints.getChildCount(); i++) {
                    View v = hints.getChildAt(i);
                    if (v.getTag().toString().contains("p")) {
                      break;
                    } else {
                      if (v.getVisibility() == View.VISIBLE) {
                        v.setVisibility(View.GONE);
                      } else {
                        v.setVisibility(View.VISIBLE);
                      }
                    }
                  }
                }
              });
          txt.setTag("p");
        } else {
          txt.setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View _view) {
                  text.setText(
                      text.getText().toString().concat(((TextView) _view).getText().toString()));
                  edittext.setText(text.getText().toString());
                }
              });
          txt.setVisibility(View.GONE);
          txt.setTag("n");
        }
        hints.addView(txt);
      }
    }

    public int pxToDp(int px) {
      return Utils.convertPixelsToDp(getApplicationContext(), (px));
    }

    public String getType() {
      return this.type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }

  class Circle extends LinearLayout {
    public Node node;
    LinearLayout parent, circle;

    public Circle(Context ctx) {
      super(ctx);
      LayoutInflater thisLL = getLayoutInflater();
      View thisVV = thisLL.inflate(R.layout.for_circles, null);
      this.addView(thisVV);
      circle = thisVV.findViewById(R.id.circle);
      parent = thisVV.findViewById(R.id.parent);
      update();
      circle.setBackground(
          new GradientDrawable() {
            public GradientDrawable getIns(int a, int b, int c, int d) {
              this.setCornerRadius(a);
              this.setStroke(b, c);
              this.setColor(d);
              return this;
            }
          }.getIns(90, 1, Color.GRAY, Color.TRANSPARENT));
    }

    public void update() {
      _setHW(
          this,
          Utils.convertPixelsToDp(getApplicationContext(), 90) * scale,
          Utils.convertPixelsToDp(getApplicationContext(), 15) * scale);
      _setHW(
          circle,
          Utils.convertPixelsToDp(getApplicationContext(), 15) * scale,
          Utils.convertPixelsToDp(getApplicationContext(), 15) * scale);
    }

    public void setSelect(boolean b) {
      if (b)
        circle.setBackground(
            new GradientDrawable() {
              public GradientDrawable getIns(int a, int b, int c, int d) {
                this.setCornerRadius(a);
                this.setStroke(b, c);
                this.setColor(d);
                return this;
              }
            }.getIns(90, 1, 0xFFFFFFFF, Color.TRANSPARENT));
      else
        circle.setBackground(
            new GradientDrawable() {
              public GradientDrawable getIns(int a, int b, int c, int d) {
                this.setCornerRadius(a);
                this.setStroke(b, c);
                this.setColor(d);
                return this;
              }
            }.getIns(90, 1, Color.GRAY, Color.GRAY));
    }
  }

  protected class FrameListener implements View.OnDragListener {
    public boolean onDrag(final View vm88, final DragEvent event) {
      final View v = (vm88);
      final int action = event.getAction();
      final View DV = ((View) event.getLocalState());
      /*
      for (int x = 0;x<frame.getChildCount();x++){
      	View view = frame.getChildAt(x);
      	if(view instanceof Node){
      		((Node)view).updatePos();
      	}
      }
      */
      frame.invalidate();
      switch (action) {
        case DragEvent.ACTION_DRAG_STARTED:
          for (int i = 0; i < frame.getChildCount(); i++) {
            View view = frame.getChildAt(i);
            if (view instanceof Node) {
              ((Node) view).updatePos();
            }
          }
          temp_path.reset();
          try {
            ((Circle) v).node.path.reset();
          } catch (Exception ex) {
          }
          frame.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_ENTERED:
          v.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_LOCATION:
          // drag started
          temp_path.reset();
          c_path.reset();
          float x = (sc.getX() + (sc.circle.getMeasuredWidth() / 2));
          float y = (sc.getY() + (sc.circle.getMeasuredHeight() / 2));
          temp_path.moveTo(x, y);
          c_path.addCircle(x, y, sc.circle.getMeasuredWidth() / 2, Path.Direction.CW);
          temp_path.cubicTo(
              x + 100, y, event.getX() - 100, event.getY(), event.getX(), event.getY());
          c_path.addCircle(
              event.getX(), event.getY(), sc.circle.getMeasuredWidth() / 2, Path.Direction.CW);
          frame.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_EXITED:
          c_path.reset();
          frame.invalidate();
          v.invalidate();
          return true;
        case DragEvent.ACTION_DROP:
          // drop
          v.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_ENDED:
          v.invalidate();
          temp_path.reset();
          c_path.reset();
          frame.invalidate();
          sc.node.left_circle.setOnDragListener(new CircleListener());
          return true;
        default:
          break;
      }
      return false;
    }
  }

  protected class CircleListener implements View.OnDragListener {
    public boolean onDrag(final View vm88, final DragEvent event) {
      final Circle v = ((Circle) vm88);
      final int action = event.getAction();
      final View DV = ((View) event.getLocalState());

      switch (action) {
        case DragEvent.ACTION_DRAG_STARTED:
          temp_path.reset();
          frame.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_ENTERED:
          v.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_LOCATION:
          // drag started
          temp_path.reset();
          c_path.reset();
          float x = (sc.getX() + (sc.circle.getMeasuredWidth() / 2));
          float y = (sc.getY() + (sc.circle.getMeasuredHeight() / 2));

          float xtc = (v.circle.getX() + (v.circle.getMeasuredWidth() / 2));
          float ytc = (v.circle.getY() + (v.circle.getMeasuredHeight() / 2));

          float xt = (v.getX() + v.getMeasuredWidth() - xtc);
          float yt = (v.getY() + (v.getMeasuredHeight() / 2));
          c_path.addCircle(x, y, v.circle.getMeasuredWidth(), Path.Direction.CW);
          temp_path.moveTo(x, y);
          temp_path.cubicTo(x + 100, y, xt - 100, yt, xt, yt);
          c_path.addCircle(xt, yt, v.circle.getMeasuredWidth() / 2, Path.Direction.CW);

          for (int i = 0; i < frame.getChildCount(); i++) {
            View view = frame.getChildAt(i);
            if (view instanceof Node) {
              ((Node) view).updatePos();
            }
          }

          frame.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_EXITED:
          c_path.reset();
          frame.invalidate();
          v.invalidate();
          return true;
        case DragEvent.ACTION_DROP:
          // drop
          temp_path.reset();
          try {
            if (sc.getTag() == null) {
              sc.node.next = v.node;
            } else if (sc.getTag().toString().equals("true")) {
              sc.node.true_node = v.node;
            } else if (sc.getTag().toString().equals("false")) {
              sc.node.false_node = v.node;
            }
          } catch (Exception ex) {
            Utils.showMessage(getApplicationContext(), ex.toString());
          }
          for (int i = 0; i < frame.getChildCount(); i++) {
            View view = frame.getChildAt(i);
            if (view instanceof Node) {
              ((Node) view).updatePos();
            }
          }
          frame.invalidate();
          v.invalidate();
          return true;
        case DragEvent.ACTION_DRAG_ENDED:
          v.invalidate();
          temp_path.reset();
          c_path.reset();
          frame.invalidate();
          return true;
        default:
          break;
      }
      return false;
    }
  }

  private String[] getBodies() {
        ArrayList<String> list = new ArrayList<>();
        for (String s : hintsL) {
            if("- Items".equals(s))    continue ;
            if (s.startsWith("- ")) { 
                break;
            } else {
                list.add(s);
            }
        }
        return list.toArray(new String[0]);
    }
    
    private String[] getList(String target,boolean lastSegment){
        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(project+"/"+target,files);
        if(lastSegment)
            for(int pos=0;pos<files.size();pos++){
    		    files.set(pos,Uri.parse(files.get(pos)).getLastPathSegment());
    		}
        return files.toArray(new String[0]);
    }
    
    private String[] provideListForType(String type){
        switch(type){
            case "image":
                // _refreshList();
                // return imagesList;
                return null;
            case "Body": return getBodies();
            case "scene": return getList("scenes",true);
            case "sound": return getList("sounds",true);
            case "animation": return getList("anims",true);
            case "file": return getList("files",true);
            case "Boolean" : return new String[] {"true","false"};
            default: return null;
        }
    }


  public class Listview1Adapter extends BaseAdapter {

    ArrayList<HashMap<String, Object>> _data;

    public Listview1Adapter(ArrayList<HashMap<String, Object>> _arr) {
      _data = _arr;
    }

    @Override
    public int getCount() {
      return _data.size();
    }

    @Override
    public HashMap<String, Object> getItem(int _index) {
      return _data.get(_index);
    }

    @Override
    public long getItemId(int _index) {
      return _index;
    }

    @Override
    public View getView(final int _position, View _v, ViewGroup _container) {
      LayoutInflater _inflater = getLayoutInflater();
      View _view = _v;
      if (_view == null) {
        _view = _inflater.inflate(R.layout.imgs_dialog, null);
      }

      final LinearLayout linear1 = _view.findViewById(R.id.linear1);
      final ImageView imageview1 = _view.findViewById(R.id.imageview1);
      final TextView textview1 = _view.findViewById(R.id.textview1);

      if (_data.get(_position).get("file").toString().equals("...")) {
        imageview1.setImageResource(R.drawable.right_icon);
        textview1.setText("...");
        linear1.setOnClickListener(
            _view13 -> {
              try {
                interals.remove((interals.size() - 1));

              } catch (Exception e) {
                interals.clear();
              }
              _refreshList();
            });
      } else {
        textview1.setText(
            Uri.parse(_data.get(_position).get("file").toString()).getLastPathSegment());

        if (FileUtil.isFile(_data.get(_position).get("file").toString())) {
          linear1.setOnClickListener(
              _view1 -> {
                StringBuilder p = new StringBuilder();
                for (String s : interals) p.append("/").append(s);
                targetText.setText(
                    (p + "/")
                        .concat(
                            Uri.parse(_data.get(_position).get("file").toString())
                                .getLastPathSegment()));

                cd.dismiss();
              });
          _setImageFromFile(imageview1, _data.get(_position).get("file").toString());
        } else {
          imageview1.setImageResource(R.drawable.ic_folder_white);
          linear1.setOnClickListener(
              _view12 -> {
                interals.add(
                    Uri.parse(_data.get(_position).get("file").toString()).getLastPathSegment());

                _refreshList();
              });
        }
      }

      return _view;
    }
  }
}