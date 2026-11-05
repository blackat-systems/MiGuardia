package com.blackatsystems.miguardia.stage12.c096

import android.app.Activity
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.Button

/** A real read-only provider for a compiled synthetic image. No ordinary provider is enabled. */
class C096ImageProvider:ContentProvider() {
    override fun onCreate():Boolean {check(context!!.packageName==C096Recipe.QA);return true}
    private fun exact(uri:Uri):C096Paths {
        check(Binder.getCallingUid()==android.os.Process.myUid())
        val variant=C096Variant.entries.single{uri==uri(it)}
        return C096Paths(checkNotNull(context),variant).also{it.requireStored()}
    }
    override fun getType(uri:Uri):String {
        val attempt=C096NativeDiagnostics.image();attempt.reached(C096NativeDiagnostics.ImageEdge.TYPE_ENTERED)
        try{exact(uri);attempt.reached(C096NativeDiagnostics.ImageEdge.TYPE_RETURNED);return "image/png"}
        catch(error:Throwable){attempt.failed("C096_IMAGE_PROVIDER_REJECTED");throw error}
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        val attempt=C096NativeDiagnostics.image();attempt.reached(C096NativeDiagnostics.ImageEdge.OPEN_ENTERED)
        var opened:ParcelFileDescriptor?=null
        try{check(mode=="r");val value=ParcelFileDescriptor.open(exact(uri).source,ParcelFileDescriptor.MODE_READ_ONLY);opened=value;attempt.reached(C096NativeDiagnostics.ImageEdge.OPEN_RETURNED);return value}
        catch(error:Throwable){try{opened?.close()}catch(_:Throwable){};attempt.failed("C096_IMAGE_PROVIDER_REJECTED");throw error}
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor?=throw SecurityException("C096_PROVIDER_QUERY_EXCLUDED")
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw SecurityException("C096_PROVIDER_WRITE_EXCLUDED")
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int=throw SecurityException("C096_PROVIDER_WRITE_EXCLUDED")
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int=throw SecurityException("C096_PROVIDER_WRITE_EXCLUDED")
    companion object {internal fun uri(v:C096Variant):Uri=Uri.parse("content://"+C096Recipe.QA+".c096/"+v.run+"/source.png")}
}

/** Human initial PNG selection only. No OEM claim and no programmatic selection callback. */
class C096ImagePickerActivity:Activity() {
    private var sent=false
    override fun onCreate(state:Bundle?) {
        check(state==null);super.onCreate(state)
        val v=C096Variant.valueOf(checkNotNull(intent.getStringExtra("variant")))
        check(v==C096Variant.EDIT_DRAFT);C096Paths(this,v).requireStored()
        val attempt=C096NativeDiagnostics.image();attempt.reached(C096NativeDiagnostics.ImageEdge.PICKER_CREATED)
        val button=Button(this).apply{text="Seleccionar PNG sintético C096";setOnClickListener{
            try {
            attempt.active();check(!sent);sent=true
            // This is the only selection entry: the real button listener for this compiled EDIT RUN.
            attempt.humanSelection(v.run)
            val uri=C096ImageProvider.uri(v)
            setResult(RESULT_OK,Intent().apply { data=uri; clipData=ClipData.newRawUri("C096",uri) })
            attempt.reached(C096NativeDiagnostics.ImageEdge.RESULT_SET);finish()
            attempt.reached(C096NativeDiagnostics.ImageEdge.PICKER_FINISHED)
            } catch (_:Throwable) { attempt.failed("C096_IMAGE_PICKER_FAILED");finish() }
        }}
        val content=android.widget.FrameLayout(this)
        content.addView(button,android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.CENTER
        ).apply{setMargins(32,32,32,32)})
        setContentView(content)
    }
}
