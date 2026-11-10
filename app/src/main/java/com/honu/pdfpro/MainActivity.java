package com.honu.pdfpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int OPEN = 10, PICK_IMAGE = 11, SAVE = 12;
    private static final int NAVY = Color.rgb(8, 27, 62);
    private static final int CYAN = Color.rgb(0, 183, 220);
    private static final int RED = Color.rgb(214, 37, 62);
    private enum Tool { NONE, TEXT, REPLACE, IMAGE, HIGHLIGHT, WHITEOUT, DRAW }
    private Tool tool = Tool.NONE;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pdfFd;
    private Uri source;
    private final ArrayList<Integer> pages = new ArrayList<>();
    private final Map<Integer,Bitmap> edited = new HashMap<>();
    private final Map<Integer,ArrayDeque<Bitmap>> history = new HashMap<>();
    private Bitmap current, pendingImage;
    private int position = 0, ink = NAVY;
    private String pendingText = "";
    private ImageView pageView;
    private TextView status;
    private float startX, startY, lastX, lastY;
    private boolean activeStroke;
    private Canvas strokeCanvas;
    private Paint strokePaint;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        setContentView(new Splash(this));
        new Handler(Looper.getMainLooper()).postDelayed(this::showEditor, 2850);
    }

    private void showEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(239,244,249));
        TextView title = new TextView(this);
        title.setText("  HONU  ·  PDF Pro");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setBackgroundColor(NAVY);
        title.setPadding(dp(12), dp(15), dp(12), dp(15));
        root.addView(title, new LinearLayout.LayoutParams(-1,dp(66)));

        HorizontalScrollView ribbon = new HorizontalScrollView(this);
        ribbon.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(6),dp(7),dp(6),dp(7));
        actions.setBackgroundColor(Color.rgb(220,233,245));
        ribbon.addView(actions);
        root.addView(ribbon);
        add(actions,"Open",()->openPdf());
        add(actions,"Save PDF",()->savePdf());
        add(actions,"◀",()->go(-1));
        add(actions,"▶",()->go(1));
        add(actions,"Add Text",()->prompt(Tool.TEXT,"Text to add"));
        add(actions,"Replace Text",()->{ if (requirePage()) choose(Tool.REPLACE,"Drag over old text, then type replacement"); });
        add(actions,"Add Photo",()->pickPhoto());
        add(actions,"Highlight",()->choose(Tool.HIGHLIGHT,"Drag over text to highlight"));
        add(actions,"Whiteout",()->choose(Tool.WHITEOUT,"Drag to cover text visually"));
        add(actions,"Draw / Sign",()->choose(Tool.DRAW,"Draw your signature with a finger"));
        add(actions,"Ink Color",()->chooseColor());
        add(actions,"Rotate",()->rotate());
        add(actions,"Delete Page",()->deletePage());
        add(actions,"Undo",()->undo());

        status = new TextView(this);
        status.setText("Open a PDF to begin.  •  Edits are flattened on export.");
        status.setTextColor(NAVY);
        status.setTextSize(13);
        status.setPadding(dp(12),dp(11),dp(12),dp(10));
        root.addView(status);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(207,217,231));
        pageView = new ImageView(this);
        pageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageView.setAdjustViewBounds(true);
        frame.addView(pageView,new FrameLayout.LayoutParams(-1,-1));
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        pageView.setOnTouchListener((view,event)->touch(event));
        TextView footer = new TextView(this);
        footer.setText("HONU PDF Pro  •  Free preview  •  Visual PDF editing");
        footer.setTextColor(Color.rgb(108,117,132));
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0,dp(7),0,dp(7));
        root.addView(footer);
        setContentView(root);
        Intent launch = getIntent();
        if (launch != null && Intent.ACTION_VIEW.equals(launch.getAction()) && launch.getData() != null) load(launch.getData());
    }

    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void add(LinearLayout to,String name,Runnable fn) {
        Button b = new Button(this);
        b.setText(name);
        b.setTextSize(11);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(49));
        lp.setMargins(dp(3),0,dp(3),0);
        to.addView(b,lp);
        b.setOnClickListener(v->fn.run());
    }
    private void message(String msg) { Toast.makeText(this,msg,Toast.LENGTH_SHORT).show(); }
    private boolean requirePage() {
        if (current==null) { message("Open a PDF first"); return false; }
        return true;
    }
    private void choose(Tool next,String hint) {
        if (!requirePage()) return;
        tool=next;
        status.setText("Page "+(position+1)+"/"+pages.size()+" • "+hint);
    }
    private void openPdf() {
        Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("application/pdf");
        startActivityForResult(in,OPEN);
    }
    private void pickPhoto() {
        if (!requirePage()) return;
        Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("image/*");
        startActivityForResult(in,PICK_IMAGE);
    }
    private void savePdf() {
        if (!requirePage()) return;
        Intent in=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("application/pdf");
        in.putExtra(Intent.EXTRA_TITLE,"HONU-Edited.pdf");
        startActivityForResult(in,SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri u=data.getData();
        if (request==OPEN) load(u);
        else if (request==PICK_IMAGE) {
            try(InputStream stream=getContentResolver().openInputStream(u)) {
                pendingImage=BitmapFactory.decodeStream(stream);
                if(pendingImage==null) throw new Exception("Cannot decode image");
                choose(Tool.IMAGE,"Tap the PDF page to insert photo");
            } catch(Exception e){ message("Image error: "+e.getMessage()); }
        } else if (request==SAVE) export(u);
    }
    private void closeDocument() {
        if(renderer!=null) { try{renderer.close();}catch(Exception ignored){} renderer=null; }
        if(pdfFd!=null) { try{pdfFd.close();}catch(Exception ignored){} pdfFd=null; }
        pages.clear();
        edited.clear();
        history.clear();
        current=null;
    }
    private void load(Uri u) {
        closeDocument();
        try {
            pdfFd=getContentResolver().openFileDescriptor(u,"r");
            renderer=new PdfRenderer(pdfFd);
            if(renderer.getPageCount()<1) throw new Exception("Empty PDF");
            source=u;
            for(int i=0;i<renderer.getPageCount();i++) pages.add(i);
            position=0; tool=Tool.NONE; showPage();
        } catch(Exception e) {
            message("Cannot open PDF: "+e.getMessage());
            closeDocument();
        }
    }
    private Bitmap getBitmap(int id) throws Exception {
        Bitmap existing=edited.get(id);
        if(existing!=null) return existing;
        PdfRenderer.Page page=renderer.openPage(id);
        try {
            float scale=Math.min(2.2f,1200f/Math.max(1,page.getWidth()));
            int w=Math.max(1,Math.round(page.getWidth()*scale));
            int h=Math.max(1,Math.round(page.getHeight()*scale));
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } finally { page.close(); }
    }
    private void showPage() {
        if(pages.isEmpty()){ current=null; pageView.setImageDrawable(null);status.setText("No pages left");return; }
        position=Math.max(0,Math.min(position,pages.size()-1));
        try {
            current=getBitmap(pages.get(position));
            pageView.setImageBitmap(current);
            status.setText("Page "+(position+1)+" of "+pages.size()+"  •  Use editing tools, then Save PDF");
        } catch(Exception e){message("Page error: "+e.getMessage());}
    }
    private void go(int dir) { if(requirePage()){position+=dir;showPage();} }
    private void checkpoint() {
        if(!requirePage()) return;
        int id=pages.get(position);
        ArrayDeque<Bitmap> stack=history.get(id);
        if(stack==null){stack=new ArrayDeque<>();history.put(id,stack);}
        stack.push(current.copy(Bitmap.Config.ARGB_8888,true));
        while(stack.size()>6) stack.removeLast();
        if(!current.isMutable()) current=current.copy(Bitmap.Config.ARGB_8888,true);
        edited.put(id,current);
    }
    private void undo() {
        if(!requirePage()) return;
        ArrayDeque<Bitmap> stack=history.get(pages.get(position));
        if(stack==null || stack.isEmpty()){message("Nothing to undo on this page");return;}
        current=stack.pop(); edited.put(pages.get(position),current); pageView.setImageBitmap(current);
    }
    private void refresh() {
        edited.put(pages.get(position),current);
        pageView.setImageBitmap(current);
        pageView.invalidate();
    }
    private void prompt(Tool next,String title) {
        if(!requirePage()) return;
        EditText input=new EditText(this);
        input.setSingleLine(false);
        input.setPadding(dp(16),dp(12),dp(16),dp(12));
        new AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Place on page",(dlg,which)->{
                String s=input.getText().toString();
                if(s.trim().isEmpty()) return;
                pendingText=s;
                choose(next,"Tap where you want to place the text");
            }).show();
    }
    private void chooseColor() {
        final String[] names={"Dark Blue","Red","Black","Green","Purple"};
        final int[] colors={NAVY,RED,Color.BLACK,Color.rgb(2,129,80),Color.rgb(126,50,154)};
        new AlertDialog.Builder(this).setTitle("Pen / text color")
          .setItems(names,(d,index)->{ink=colors[index];message(names[index]+" selected");}).show();
    }
    private float[] coords(MotionEvent e) {
        Matrix inverse=new Matrix();
        if(!pageView.getImageMatrix().invert(inverse)) return null;
        float[] xy={e.getX(),e.getY()};
        inverse.mapPoints(xy);
        return xy;
    }
    private boolean touch(MotionEvent e) {
        if(current==null || tool==Tool.NONE) return false;
        float[] xy=coords(e);
        if(xy==null) return true;
        float x=Math.max(0,Math.min(current.getWidth(),xy[0]));
        float y=Math.max(0,Math.min(current.getHeight(),xy[1]));
        if(e.getAction()==MotionEvent.ACTION_DOWN) {
            startX=lastX=x;startY=lastY=y;
            if(tool==Tool.DRAW) {
                checkpoint();activeStroke=true;
                strokeCanvas=new Canvas(current);
                strokePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setColor(ink); strokePaint.setStrokeWidth(6f);
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStyle(Paint.Style.STROKE);
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE) {
            if(tool==Tool.DRAW && activeStroke) {
                strokeCanvas.drawLine(lastX,lastY,x,y,strokePaint);
                lastX=x;lastY=y;refresh();
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP) {
            if(tool==Tool.DRAW) {
                if(activeStroke) {strokeCanvas.drawLine(lastX,lastY,x,y,strokePaint);activeStroke=false;refresh();}
                return true;
            }
            if(tool==Tool.TEXT) {
                checkpoint();
                drawText(new Canvas(current),pendingText,x,y);
                refresh(); tool=Tool.NONE; return true;
            }
            if(tool==Tool.IMAGE) {
                if(pendingImage==null) {message("Select image again");return true;}
                checkpoint();
                Canvas c=new Canvas(current);
                float width=Math.min(current.getWidth()*.38f,340f);
                float height=width*pendingImage.getHeight()/Math.max(1,pendingImage.getWidth());
                RectF dest=new RectF(x,y,x+width,y+height);
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                c.drawBitmap(pendingImage,null,dest,p);
                refresh();tool=Tool.NONE;return true;
            }
            RectF box=new RectF(Math.min(startX,x),Math.min(startY,y),Math.max(startX,x),Math.max(startY,y));
            if(box.width()<8) box.right=box.left+150;
            if(box.height()<8) box.bottom=box.top+45;
            if(tool==Tool.REPLACE) {
                final RectF region=new RectF(box);
                EditText input=new EditText(this);
                input.setPadding(dp(16),dp(10),dp(16),dp(10));
                new AlertDialog.Builder(this).setTitle("Visual text replacement")
                    .setMessage("Old text will be visually covered, not changed as a searchable PDF text object.")
                    .setView(input).setNegativeButton("Cancel",null)
                    .setPositiveButton("Replace",(dialog,which)->{
                        checkpoint();
                        Canvas c=new Canvas(current);
                        Paint cover=new Paint();
                        cover.setColor(Color.WHITE);
                        c.drawRect(region,cover);
                        drawText(c,input.getText().toString(),region.left,region.bottom-6);
                        refresh();tool=Tool.NONE;
                    }).show();
                return true;
            }
            if(tool==Tool.HIGHLIGHT || tool==Tool.WHITEOUT) {
                checkpoint();
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(tool==Tool.WHITEOUT?Color.WHITE:Color.argb(95,255,224,0));
                new Canvas(current).drawRect(box,p);
                refresh();return true;
            }
        }
        return true;
    }
    private void drawText(Canvas c,String value,float x,float baseline) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink);p.setTextSize(36f);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        String[] lines=value.split("\\n",-1);
        for(int i=0;i<lines.length;i++) c.drawText(lines[i],x,baseline+i*43,p);
    }
    private void rotate() {
        if(!requirePage())return;
        checkpoint();
        Matrix matrix=new Matrix();
        matrix.postRotate(90);
        current=Bitmap.createBitmap(current,0,0,current.getWidth(),current.getHeight(),matrix,true);
        refresh();
    }
    private void deletePage() {
        if(!requirePage())return;
        if(pages.size()==1){message("Cannot delete the final page");return;}
        new AlertDialog.Builder(this).setTitle("Delete page "+(position+1)+"?")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                pages.remove(position);showPage();
            }).show();
    }
    private void export(Uri target) {
        if(pages.isEmpty())return;
        PdfDocument out=new PdfDocument();
        try {
            for(int i=0;i<pages.size();i++) {
                Bitmap bmp=getBitmap(pages.get(i));
                int w=595, h=Math.max(1,Math.round(w*(float)bmp.getHeight()/bmp.getWidth()));
                PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(w,h,i+1).create();
                PdfDocument.Page pg=out.startPage(info);
                pg.getCanvas().drawColor(Color.WHITE);
                pg.getCanvas().drawBitmap(bmp,null,new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));
                out.finishPage(pg);
            }
            try(OutputStream stream=getContentResolver().openOutputStream(target,"w")) {
                if(stream==null)throw new Exception("Cannot write file");
                out.writeTo(stream);
            }
            message("PDF saved successfully");
            status.setText("Saved! Export is flattened (not searchable).");
        }catch(Exception e){message("Save failed: "+e.getMessage());}
        finally{out.close();}
    }
    @Override protected void onDestroy(){closeDocument();super.onDestroy();}

    private static class Splash extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start=android.os.SystemClock.uptimeMillis();
        Splash(Activity a){super(a);}
        @Override protected void onDraw(Canvas c) {
            int w=getWidth(),h=getHeight();
            c.drawColor(NAVY);
            float scale=Math.min(w/360f,h/650f);
            c.save();c.translate(w/2f,h/2f);c.scale(scale,scale);
            float t=(android.os.SystemClock.uptimeMillis()-start)/1000f;
            float fade=Math.min(1,t/1.4f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(0,49,100));c.drawCircle(0,-80,142,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);
            paint.setColor(CYAN);c.drawCircle(0,-80,142,paint);
            paint.setStyle(Paint.Style.FILL);
            drawEmoji(c,"🐢",-80+18*(float)Math.sin(t*2),-45+6*(float)Math.sin(t*4),105,fade);
            drawEmoji(c,"🐟",55+22*(float)Math.sin(t*3),-130,78,Math.min(1,Math.max(0,(t-.35f)*2)));
            drawEmoji(c,"🐅",65,-16+5*(float)Math.sin(t*2),74,Math.min(1,Math.max(0,(t-.7f)*2)));
            drawEmoji(c,"🐦",-54+28*(float)Math.sin(t*2),-170+16*(float)Math.sin(t*3),83,Math.min(1,Math.max(0,(t-1f)*2)));
            paint.setAlpha(255);
            paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(52);
            paint.setColor(Color.WHITE);
            c.drawText("HONU",0,156,paint);
            paint.setColor(RED);c.drawRoundRect(-83,177,83,211,12,12,paint);
            paint.setTextSize(23);paint.setColor(Color.WHITE);
            c.drawText("PDF PRO",0,203,paint);
            paint.setTextSize(12);paint.setColor(CYAN);
            c.drawText("TURTLE  •  FISH  •  TIGER  •  BIRD",0,254,paint);
            c.restore();
            if(t<2.8)postInvalidateDelayed(35);
        }
        private void drawEmoji(Canvas c,String emoji,float x,float y,float size,float alpha) {
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(size);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int)(255*alpha));
            c.drawText(emoji,x,y,paint);
            paint.setAlpha(255);
        }
    }
}
