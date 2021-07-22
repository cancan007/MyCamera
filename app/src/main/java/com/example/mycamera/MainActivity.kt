package com.example.mycamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mycamera.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    val REQUEST_PREVIEW = 1  // intentで遷移する先を整数で指定している
    val REQUEST_PICTURE = 2
    val REQUEST_EXTERNAL_STORAGE = 3

    lateinit var currentPhotoUri: Uri

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.radioGroup.setOnCheckedChangeListener{group, checkedId ->    //setOnCheckedChangeListener: 選択状態が変更されたときに呼ばれるリスナー
            when(checkedId){
                R.id.preview ->
                    binding.cameraButton.text = binding.preview.text
                R.id.takePicture->
                    binding.cameraButton.text = binding.takePicture.text
            }
        }

        binding.cameraButton.setOnClickListener{   // クリック時に実行
            when(binding.radioGroup.checkedRadioButtonId){
                R.id.preview -> preview()
                R.id.takePicture -> takePicture()
            }
        }

        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
            storagePermission()
        }
    }

    private fun storagePermission(){  // 権限の確認ウィンドウを表示する
        val permission = ContextCompat.checkSelfPermission(  //ContextCompat.checkSelfPermission: パーミッションが許可されたかどうかを戻り値とする
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE  // WRITE_EXTERNAL_STORAGE: 外部アプリのディレクトリに書き込みを行うパーミッションを申請
        )
        if(permission != PackageManager.PERMISSION_GRANTED){ // 許可されていればPERMISSION_GRANTED
            ActivityCompat.requestPermissions(  //requestPermissions: パーミッションを要求するダイアログを表示、選択後onRequestPermissionsResultコールバックを呼ぶ
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),  // 要求するパーミッション名の配列
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }

    override fun onRequestPermissionsResult( //ユーザーがダイアログで許可か許可しないを選択したとき呼び出される
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray  // パーミッションが許可されたか不許可か(PERMISSION_GRANTED か PERMISSION_DENIED)
    ){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode){
            REQUEST_EXTERNAL_STORAGE -> {
                binding.cameraButton.isEnabled = grantResults.isNotEmpty() &&  // 不許可であればボタンを利用不可状態、許可であれば利用可能
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun preview() {
        // 暗黙的インテント: 意図だけを伝え、どの機能が用いられるかはAndroid OSに任せる
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->  // also: スコープ関数の一つ
            intent.resolveActivity(packageManager)?.also{  // resolveActivity: インテントを処理できるアプリがあるかチェックします  PackageManager: インストールされている様々な情報を持つクラス
                startActivityForResult(intent, REQUEST_PREVIEW)  // startActivityForResult: アクティビティを起動してその結果を受け取る
            }
        }
    }
    private fun takePicture() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also{ intent ->
            intent.resolveActivity(packageManager)?.also{
                val time: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val values = ContentValues().apply{
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${time}_.jpg")  // ファイルの名称にタイムスタンプを使用
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")  // 形式を指定
                }
                val collection = MediaStore.Images.Media.getContentUri("external")  //getContentUriメソッドで、画像メディアを格納するための外部ストレージ("external")の場所を取得,internalで内部ストレージ
                val photoUri = contentResolver.insert(collection, values)  //insert: 格納する場所と格納するものを指定して、最終的な保存先を確保, 戻り値として新しく追加したコンテンツのUriを返す
                photoUri?.let{
                    currentPhotoUri = it  // 上で取得したファイルのUriを保持しておく
                }
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)  //カメラが撮影した画像の保存先を指定
                startActivityForResult(intent, REQUEST_PICTURE)  // カメラを起動(REQUEST_PICTUREでどこから帰ってきたのかを判別)
            }
        }
    }

    override fun onActivityResult(requestCode: Int,  // startActivityForResultで起動したアクティビティが閉じられると、onActivityResultが呼び出される
                                   resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_PREVIEW && resultCode == RESULT_OK){  // resultCodeに他アクティビティでの処理の結果が入っている   正常に動作すれば(📷撮影が成功していれば)RESULT_OKが入っている
            val imageBitmap = data?.extras?.get("data") as Bitmap  // 「data」キーで撮影した画像データが入っている
            binding.imageView.setImageBitmap(imageBitmap)  // imageViewに取得した画像を表示
        } else if(requestCode == REQUEST_PICTURE){
            when(resultCode){
                RESULT_OK ->{  // この項はInstagramなどのSNSに共有できるようにするためのもの
                    Intent(Intent.ACTION_SEND).also{share ->
                        share.type = "image/*"
                        share.putExtra(Intent.EXTRA_STREAM, currentPhotoUri)   //共有する画像ファイルの場所をUriで指定します
                        startActivity(Intent.createChooser(share, "Share to"))  //createChooser: アプリ選択画面を表示するためのインテントを作成し、startActivityに渡している
                    }
                }
                else ->{
                    contentResolver.delete(currentPhotoUri, null, null)  //撮影がキャンセルされたときにファイルが不要となりますのでdeleteで削除している
                }
            }
        }
    }
}