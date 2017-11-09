package com.example.android.phot100;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import id.zelory.compressor.Compressor;

public class Settings extends AppCompatActivity {

    private Toolbar mToolbar;
    private CircleImageView mImage;
    private TextView mName;
    private Button mppbtn;

    private DatabaseReference mUserDatabase;
    private FirebaseUser mCurrentUser;
    private StorageReference mStorageReference;

    private ProgressDialog mImageProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mImage = (CircleImageView) findViewById(R.id.settings_image);
        mName = (TextView) findViewById(R.id.settings_display_name);
        mppbtn = (Button) findViewById(R.id.change_pp_btn);

        mStorageReference = FirebaseStorage.getInstance().getReference();

        mToolbar = (Toolbar) findViewById(R.id.settings_bar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("Account Settings");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCurrentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = mCurrentUser.getUid();
        mUserDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child(currentUid);
        mUserDatabase.keepSynced(true);

        mUserDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                String name = dataSnapshot.child("name").getValue().toString();
                final String image = dataSnapshot.child("Image").getValue().toString();
                String thumb_image = dataSnapshot.child("thumb_image").getValue().toString();
                mName.setText(name);
                if(!image.equals("default")){

                Picasso.with(Settings.this).load(image).networkPolicy(NetworkPolicy.OFFLINE).placeholder(R.drawable.default_image).into(mImage, new Callback() {
                    @Override
                    public void onSuccess() {}
                    @Override
                    public void onError() {
                        Picasso.with(Settings.this).load(image).placeholder(R.drawable.default_image).into(mImage);
                    }
                });

            }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mppbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CropImage.activity()
                        .setGuidelines(CropImageView.Guidelines.ON)
                        .setAspectRatio(1, 1)
                        .start(Settings.this);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {

                mImageProgress = new ProgressDialog(Settings.this);
                mImageProgress.setTitle("Uploading your Image...");
                mImageProgress.setMessage("Please wait while your new profile picture is being added");
                mImageProgress.setCanceledOnTouchOutside(false);
                mImageProgress.show();

                Uri resultUri = result.getUri();
                String currentUid = mCurrentUser.getUid();

                File thumb_path = new File(resultUri.getPath());
                Bitmap thumbBitmap = new Compressor(this)
                        .setMaxWidth(200)
                        .setMaxHeight(200)
                        .setQuality(75)
                        .compressToBitmap(thumb_path);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                final byte[] thumb_byte = baos.toByteArray();

                StorageReference filePath = mStorageReference.child("profile_images").child(currentUid + ".jpg");

                final StorageReference thumb_filePath = mStorageReference.child("profile_images").child("thumbs").child(currentUid + ".jpg");

                filePath.putFile(resultUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if(task.isSuccessful()){
                            final String downloadUrl = task.getResult().getDownloadUrl().toString();

                            UploadTask uploadTask = thumb_filePath.putBytes(thumb_byte);
                            uploadTask.addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> thumb_task) {

                                    final String thumbDownloadUrl = thumb_task.getResult().getDownloadUrl().toString();

                                    if(thumb_task.isSuccessful()){

                                        Map update_hashmap = new HashMap<>();
                                        update_hashmap.put("Image", downloadUrl);
                                        update_hashmap.put("thumb_image", thumbDownloadUrl);
                                        mUserDatabase.updateChildren(update_hashmap).addOnCompleteListener(new OnCompleteListener<Void>() {
                                             @Override
                                             public void onComplete(@NonNull Task<Void> task) {
                                                 mImageProgress.dismiss();
                                             }
                                         });
                                     }
                                     else{
                                         Toast.makeText(Settings.this, "Error updating thumbnail", Toast.LENGTH_SHORT).show();
                                         mImageProgress.dismiss();
                                     }
                                }
                            });

                        }
                        else{
                            Toast.makeText(Settings.this, "Error. Profile picture could not be updated", Toast.LENGTH_SHORT).show();
                            mImageProgress.dismiss();
                        }
                    }
                });
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
            }
        }
    }
}
