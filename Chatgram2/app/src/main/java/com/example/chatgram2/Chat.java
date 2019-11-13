package com.example.chatgram2;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.Entity;
import com.facebook.crypto.exception.CryptoInitializationException;
import com.facebook.crypto.exception.KeyChainException;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.soloader.SoLoader;
import com.facebook.crypto.util.SystemNativeCryptoLibrary;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class Chat extends AppCompatActivity {
    LinearLayout layout;
    RelativeLayout layout_2;
    ImageView sendButton;
    EditText messageArea;
    ScrollView scrollView;
    Firebase reference1, reference2;
    Button upload;
    String message=null;

    Uri filePath;

    KeyChain keyChain ;
    Crypto crypto ;

    private final int PICK_FILE_REQUEST = 71;
    private static final int REQUEST_WRITE_PERMISSION = 786;


    FirebaseStorage storage;
    StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actvit_chat);

        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        SoLoader.init(Chat.this, false);
        keyChain = new SharedPrefsBackedKeyChain (Chat.this,  CryptoConfig.KEY_256);
        crypto = AndroidConceal.get().createDefaultCrypto(keyChain);

        layout = (LinearLayout) findViewById(R.id.layout1);
        layout_2 = (RelativeLayout)findViewById(R.id.layout2);
        sendButton = (ImageView)findViewById(R.id.sendButton);
        messageArea = (EditText)findViewById(R.id.messageArea);
        scrollView = (ScrollView)findViewById(R.id.scrollView);
        upload = (Button)findViewById(R.id.uploadButton);
        requestPermission();

        Firebase.setAndroidContext(this);
        reference1 = new Firebase("https://chatgram2-c4a1b.firebaseio.com/messages/" + UserDetails.username + "_" + UserDetails.chatWith);
        reference2 = new Firebase("https://chatgram2-c4a1b.firebaseio.com/messages/" + UserDetails.chatWith + "_" + UserDetails.username);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageText = null;
                try {
                    messageText = encryptMsg(messageArea.getText().toString());
                    Log.d("Encrypted_message: ",messageText);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(!messageText.equals("")){
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("message", messageText);
                    map.put("user", UserDetails.username);
                    reference1.push().setValue(map);
                    reference2.push().setValue(map);
                    messageArea.setText("");
                }
            }
        });

        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadFile();
            }
        });

        reference1.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Map map = dataSnapshot.getValue(Map.class);
                try {
                    message = decryptMsg(map.get("message").toString());
                    Log.d("decrypted_message: ",message );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String userName = map.get("user").toString();

                if(userName.equals(UserDetails.username)){
                    addMessageBox("You:-\n" + message, 1);
                }
                else{
                    addMessageBox(UserDetails.chatWith + ":-\n" + message, 2);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    private void uploadFile() {
        Intent intent = new Intent();
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select File"),PICK_FILE_REQUEST);


    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode, Intent data)
    {
        super.onActivityResult(requestCode,resultCode,data);
        String cipher=null;
        storageReference = FirebaseStorage.getInstance().getReference("file"+System.currentTimeMillis());
        if(requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null)
        {
            filePath = data.getData();
            Log.d("First_F",filePath.toString());
            String abspath = getRealpath(filePath);
            Log.d("Absolute Path",abspath);
            final ProgressDialog progressDialog = new ProgressDialog(Chat.this);
            progressDialog.setTitle("Uploading...");
            progressDialog.show();

            try {
                long startime = System.nanoTime();
                encryptFile(abspath);
                long endtime = System.nanoTime();
                long duration = endtime - startime;
                Log.d("Encryption Time: ", String.valueOf(duration));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (KeyChainException e) {
                e.printStackTrace();
            } catch (CryptoInitializationException e) {
                e.printStackTrace();
            }
            cipher = abspath.concat(".crypt");
            Log.d("Cipher Path",cipher);

            filePath = Uri.fromFile(new File(cipher));
            Log.d("Second_F",filePath.toString());


            UploadTask uploadTask = storageReference.putFile(filePath);

            Task<Uri> task = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if(!task.isSuccessful()) {
                        progressDialog.dismiss();
                        Toast.makeText(Chat.this,"Failed",Toast.LENGTH_SHORT).show();
                    }
                    return storageReference.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if(task.isSuccessful()) {
                        Toast.makeText(Chat.this,"Uploaded",Toast.LENGTH_SHORT).show();
                        String url = null;
                        try {
                            url = encryptMsg(task.getResult().toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.d("URL: ",url);

                        Map<String, String> map = new HashMap<String, String>();
                        map.put("message",url);
                        map.put("user", UserDetails.username);
                        reference1.push().setValue(map);
                        reference2.push().setValue(map);
                        messageArea.setText("");
                    }
                }
            });


        }
        File file = new File(cipher);
        boolean deleted = file.delete();
        if (!deleted){
            Log.d("FILE ERROR","error" );
        }

    }

    public void addMessageBox(String message, int type){
        TextView textView = new TextView(Chat.this);
        textView.setText(message);
        textView.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.weight = 1.0f;

        if(type == 1) {

            if(message.contains("https://firebase"))
            {
                Button buttonView = new Button (Chat.this);
                buttonView.setText ("Download");
                buttonView.setOnClickListener (downButtonlistener);
                layout.addView (buttonView, lp2);

            }
            else
                layout.addView(textView);

            lp2.gravity = Gravity.RIGHT;
            textView.setBackgroundResource(R.drawable.sent);
        }
        else{
            if(message.contains("https://firebase"))
            {
                Button buttonView = new Button (Chat.this);
                buttonView.setText ("Download");
                buttonView.setOnClickListener (downButtonlistener);
                layout.addView (buttonView, lp2);

            }
            else
                layout.addView(textView);

            lp2.gravity = Gravity.LEFT;
            textView.setBackgroundResource(R.drawable.recieve);
        }
        textView.setLayoutParams(lp2);
        //layout.addView(textView);
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    public View.OnClickListener downButtonlistener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
                downloadFile(message);
        }
    };

    public void downloadFile(String downloadURL)
    {
        Log.d("PATH",downloadURL);
        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(downloadURL);

        Toast.makeText(Chat.this,"Downloading..",Toast.LENGTH_SHORT).show();

        final File filepath = new File(Environment.getExternalStorageDirectory(),"Chatgram2");
        Log.d("d_filepath_first: ",filepath.toString());

        if(!filepath.exists()){
            filepath.mkdirs();
        }

        final File getfile = new File(filepath,"file"+System.currentTimeMillis()+".crypt");
        Log.d("d_getfile_first: ",getfile.toString());

        storageReference.getFile(getfile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                Toast.makeText(Chat.this,"Downloaded",Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(Chat.this, "Download Failed", Toast.LENGTH_LONG).show();
            }
        }).addOnCompleteListener(new OnCompleteListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<FileDownloadTask.TaskSnapshot> task) {
                String saved=null;
                try {
                    String abs_path = getfile.getAbsolutePath();
                    Log.d("d_abs_path: ",abs_path);
                    long startime = System.nanoTime();
                    saved= decryptFile(abs_path);
                    long endtime = System.nanoTime();
                    long duration = endtime - startime;
                    Log.d("Decryption Time: ", String.valueOf(duration));
                    Log.d("d_saved: ",saved);
                } catch (Exception e) {
                    e.printStackTrace ();
                }
                Toast.makeText(Chat.this, "file saved to"+  saved, Toast.LENGTH_LONG).show();
                
            }
        });
        File file = new File(getfile.getAbsolutePath());
        boolean deleted = file.delete();
        if (!deleted){
            Log.d("FILE ERROR","error" );
        }

        //final File
    }
    public String getRealpath ( Uri uri){
        String path = null, image_id = null;

        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            image_id = cursor.getString(0);
            image_id = image_id.substring(image_id.lastIndexOf(":") + 1);
            cursor.close();
        }
        if (uri.toString ().contains ("image")) {
            cursor = getContentResolver ().query (android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.Media._ID + " = ? ", new String[]{image_id}, null);
        }
        else {
            cursor = getContentResolver ().query (android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.Media._ID + " = ? ", new String[]{image_id}, null);

        }

        if (cursor!=null) {
            cursor.moveToFirst();
            if (uri.toString ().contains ("image")) {
                path = cursor.getString (cursor.getColumnIndex (MediaStore.Images.Media.DATA));
                cursor.close ();
            }
            else{
                path = cursor.getString (cursor.getColumnIndex (MediaStore.Video.Media.DATA));
                cursor.close ();
            }
        }
        return path;

    }

    private String encryptMsg(String message) throws Exception {
        ByteArrayOutputStream byteout = new ByteArrayOutputStream();

        Entity entity = new Entity("12345678");
        OutputStream cryptoStream = crypto.getCipherOutputStream(byteout, entity);
        cryptoStream.write(message.getBytes("UTF-8"));
        cryptoStream.close();

        String encryption = Base64.encodeToString(byteout.toByteArray(), Base64.DEFAULT);
        byteout.close();

        Log.d("within_encryption",encryption);

        return encryption;

    }

    private String decryptMsg(String message) throws Exception{
        ByteArrayInputStream bytein = new ByteArrayInputStream(Base64.decode(message, Base64.DEFAULT));
        Entity entity = new Entity("12345678");
        InputStream cryptoStream = crypto.getCipherInputStream(bytein, entity);
        ByteArrayOutputStream byteout = new ByteArrayOutputStream();
        int read = 0;
        byte[] buffer = new byte[1024];
        while ((read = cryptoStream.read(buffer)) != -1) {
            byteout.write(buffer, 0, read);
        }
        cryptoStream.close();
        String result = new String(byteout.toByteArray(), "UTF-8");
        bytein.close();
        byteout.close();
        return result;
    }

    private void encryptFile(String realPath)throws IOException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, CryptoInitializationException, KeyChainException {
        /*FileInputStream fis = new FileInputStream (realPath);
        FileOutputStream fos;*/
        String path;

        OutputStream  n_File = null;
        Entity entity = new Entity("12345678");

        path = realPath.concat(".crypt");

        n_File = new BufferedOutputStream(new FileOutputStream(path));

        OutputStream os = crypto.getCipherOutputStream(n_File,entity);

        int b;
        byte[] d = new byte[1024];

        BufferedInputStream bIS = new BufferedInputStream(new FileInputStream(realPath));
        while ((b = bIS.read(d)) != -1)
        {
            os.write(d, 0, b);
        }

        os.close();
        bIS.close();


    }

    private String decryptFile(String path) throws IOException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException {

        String newPath = null;
        Log.d("d_path: ",path);

        InputStream is = new BufferedInputStream(new FileInputStream(path));

        Entity entity = new Entity("12345678");

        InputStream nis = null;

        try {
            nis = crypto.getCipherInputStream(is, entity);
        } catch (CryptoInitializationException e) {
            e.printStackTrace();
        } catch (KeyChainException e) {
            e.printStackTrace();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        newPath = path.replace(".crypt","");

        OutputStream os = new FileOutputStream(newPath);
        BufferedInputStream bIS = new BufferedInputStream(nis);

        int b;
        byte[] d = new byte[1024];

        while((b = bIS.read(d))!=-1){
            os.write(d,0,b);
        }

        bIS.close();
        baos.writeTo(os);
        is.close();
        os.close();

        return newPath;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
        }
    }

}
