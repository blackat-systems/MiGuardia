package com.blackatsystems.miguardia.stage12.c096

import android.app.Application
import android.content.*
import android.os.*
import android.view.*
import android.view.inspector.WindowInspector
import androidx.compose.ui.window.DialogWindowProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.*
import com.blackatsystems.miguardia.ui.management.*
import com.blackatsystems.miguardia.ui.worksetup.*
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import kotlinx.coroutines.*
import java.io.File
import java.time.Clock
import java.util.UUID
import org.json.*

class C096QaApplication : Application() {
    val generation: String = UUID.randomUUID().toString()
    val processStart: Long = android.os.Process.getStartElapsedRealtime()
    val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun attachBaseContext(base:Context) {
        check(base.packageName==C096Recipe.QA && Build.VERSION.SDK_INT==37)
        check(C096Recipe.PROGRAM=="ec22c846-41d8-409e-9d64-d3cbe87095a8")
        // Fixed recipe checked before providers. No storage is opened or seeded here.
        super.attachBaseContext(base)
    }
    override fun onCreate() {
        super.onCreate()
        check(packageName == C096Recipe.QA && Build.VERSION.SDK_INT == 37)
        @Suppress("DEPRECATION") val pkg=packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
        check(pkg.providers.orEmpty().filter{it.enabled}.map{it.name}==listOf(C096ImageProvider::class.java.name)) { "C096_PROVIDER_PROFILE" }
        @Suppress("DEPRECATION") val activities=packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_ACTIVITIES or android.content.pm.PackageManager.GET_RECEIVERS or android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
        val hosts=setOf(C096ViewDetailActivity::class.java.name,C096EditDraftActivity::class.java.name)
        check(activities.activities.orEmpty().filter{it.enabled}.map{it.name}.toSet()==hosts+C096ImagePickerActivity::class.java.name) { "C096_ACTIVITY_PROFILE" }
        check(activities.activities.orEmpty().filter{it.name in hosts}.all{it.exported && it.permission=="android.permission.DUMP"}) { "C096_LAUNCH_PERMISSION" }
        check(activities.activities.orEmpty().single{it.name==C096ImagePickerActivity::class.java.name}.exported==false)
        check(activities.receivers.orEmpty().none{it.enabled} && activities.services.orEmpty().none{it.enabled}) { "C096_STARTUP_PROFILE" }
        // No MiGuardiaApplication startup, store, recovery, alarm, Widget or preferences initialization.
    }
}

class C096ViewDetailActivity : C096Activity() { internal override val variant=C096Variant.VIEW_DETAIL }
class C096EditDraftActivity : C096Activity() { internal override val variant=C096Variant.EDIT_DRAFT }

abstract class C096Activity : ComponentActivity() {
    internal abstract val variant:C096Variant
    private val app get()=application as C096QaApplication
    private lateinit var paths:C096Paths
    private lateinit var own:C096Context
    private lateinit var fixture:C096Fixture
    private var store:LocalDataStore?=null
    private val modelJobs=mutableListOf<Job>()
    private val handles=linkedMapOf<String,SavedStateHandle>()
    private var composition:C096TemporalComposition?=null
    private lateinit var calendar:CalendarViewModel
    private lateinit var manual:V2ManualShiftLoadViewModel
    private lateinit var work:WorkSetupViewModel
    private var readyA=false
    private var restored=false
    private var saved=false
    private var stopped=false
    private var backgroundReadyPosted=false
    private var backgroundRequested=false
    private var closed=false
    private var first:Throwable?=null
    private var firstPhase:String?=null
    private var phase="ENTRY"
    private var originalGeneration:String?=null
    private val events=mutableListOf<String>()
    private val hostStarted=SystemClock.elapsedRealtime()
    private val phaseTimings=mutableListOf<JSONObject>()
    private var emittedFailure=false
    private var assertionPoint="NONE"
    private val assertionChecks=linkedMapOf<String,Boolean>()
    private var manualIds=0
    private var captureOrdinal=0
    private var closeBounds:List<Int>?=null
    private val imageAttempt=C096NativeDiagnostics.ImageAttempt()
    private fun active(){check(first==null && !closed){"C096_AFTER_FIRST_NEGATIVE"}}
    private fun retain(error:Throwable){imageAttempt.retainFirst{if(first==null){first=error;firstPhase=phase}}}
    private fun timing(edge:String){phaseTimings+=JSONObject().put("phase",phase).put("edge",edge).put("elapsed_ms",SystemClock.elapsedRealtime()-hostStarted)}
    private suspend fun stage(name:String,body:suspend()->Unit){active();phase=name;events+="ENTERED:"+name;timing("ENTERED");try{checkpoint("ENTERED");body();active();events+="RETURNED:"+name;timing("RETURNED");checkpoint("RETURNED")}catch(e:Throwable){retain(e);throw e}}
    override fun onCreate(state:Bundle?) {
        // No factory, root creation or state injection precedes Android restoration.
        restored=state!=null
        if(restored){check(state!!.getString("c096.program")==C096Recipe.PROGRAM && state.getString("c096.variant")==variant.name);originalGeneration=checkNotNull(state.getString("c096.generation"));check(originalGeneration!=app.generation)}
        super.onCreate(state)
        paths=C096Paths(this,variant);fixture=C096Fixture(variant)
        lifecycleScope.launch {
            suspend fun startup(){
                stage("PATHS") { withContext(Dispatchers.IO){if(restored)paths.requireStored() else paths.create()} }
                // Bootstrap exists before either PackageManager/APK identity lookup.
                stage("IDENTITY") { identity() }
                own=C096Context(this@C096Activity,paths)
                stage("STORE") { withContext(Dispatchers.IO){store=LocalDataStore.create(own,paths.name);if(!restored)fixture.create(checkNotNull(store),paths) else fixture.requireDatabase(C096FullStoreReadback.capture(paths))} }
                stage("MOUNT") { mount() }
                stage("READY") { await { work.uiState.value.rootState is WorkSetupState.V2Ready && calendar.uiState.value.loadState == CalendarLoadState.CONTENT } }
                if(restored) restoreOnly() else enterThroughUi()
            }
            try{
                if(variant==C096Variant.EDIT_DRAFT && !restored)withTimeout(150_000){startup()} else startup()
            }catch(e:Throwable){fail(e)}
        }
    }
    private fun <T:ViewModel> model(key:String,type:Class<T>,factory:ViewModelProvider.Factory):T {
        val guarded=object:ViewModelProvider.Factory{
            override fun <V:ViewModel> create(modelClass:Class<V>,extras:CreationExtras):V {
                active();check(key !in handles);handles[key]=extras.createSavedStateHandle()
                val value=factory.create(modelClass,extras)
                modelJobs+=checkNotNull(value.viewModelScope.coroutineContext[Job]);active();return value
            }
        }
        return ViewModelProvider(this,guarded).get("C096/"+variant.run+"/"+key,type)
    }
    private fun mount() {
        val s=checkNotNull(store);val gate=LocalDataMutationGate();val clock=Clock.system(C096Recipe.zone)
        calendar=model("calendar",CalendarViewModel::class.java,CalendarViewModel.Factory(s.shifts,s.explicitDayStatuses,s.medicalLeaves,s.holidays,s.vacations,clock,s.shiftSituations,SchedulePhotoFileStore(own),gate,s.calendarBatches))
        manual=model("manual",V2ManualShiftLoadViewModel::class.java,V2ManualShiftLoadViewModel.Factory(s.workConfiguration,s.workCatalog,s.objectives,s.shifts,s.medicalLeaves,s.v2Shifts,s.shiftActuals,s.explicitDayStatuses,clock,C096Recipe.zone,UuidProvider{check(!restored && manualIds++==0){"C096_ID_REGENERATION"};fixture.operation},s.calendarBatches,ManualLoadNotes(own,s.shiftNotes,s.shiftSituations,gate)))
        work=model("work",WorkSetupViewModel::class.java,WorkSetupViewModel.Factory(s.workConfiguration,s.workCatalog,s.objectives,clock,{error("C096_SETUP_WRITE_FORBIDDEN")},{error("C096_WEATHER_MUTATION_FORBIDDEN")}))
        val c=C096TemporalComposition(this,::retain);composition=c
        val parent=c.initialize(this)
        setContent(parent=parent) {
            CompositionLocalProvider(LocalContext provides own) { MiGuardiaTheme {
                val cs by calendar.uiState.collectAsState();val ms by manual.uiState.collectAsState();val ws by work.uiState.collectAsState()
                MiGuardiaApp(calendarState=cs,onPreviousMonth=calendar::showPreviousMonth,onNextMonth=calendar::showNextMonth,onToday=calendar::showCurrentMonth,
                    onSelectDate=calendar::selectDate,onDismissDate=calendar::clearSelectedDate,onRetry=calendar::retry,
                    onEnterCalendarEditMode=calendar::enterEditMode,onEnterManualCalendarSelection=calendar::enterManualLoadSelection,onEnterHolidayCalendarSelection=calendar::enterHolidaySelection,
                    onSetCalendarSelectionPurpose=calendar::setSelectionPurpose,onCalendarCollapsedChange=calendar::setCalendarCollapsed,
                    onEditSelectionChange=calendar::setEditSelectedDates,onConfirmEditSelection=calendar::confirmEditSelection,onResumeEditSelection=calendar::resumeEditSelection,onFinishCalendarEditMode=calendar::finishEditMode,
                    v2ManualShiftLoadState=ms,v2ManualShiftLoadActions=V2ManualShiftLoadActions.from(manual).copy(importNoteImages=::receiveImageResult),workSetupState=ws,workSetupActions=WorkSetupActions.from(work))
            } }
        }
        c.bindContent(this)
    }
    private suspend fun await(checkNow:()->Boolean){withTimeout(30_000){while(true){active();if(checkNow())return@withTimeout;delay(32)}}}
    private data class OwnedNode(val node:SemanticsNode,val decor:View)
    private fun roots():List<Pair<ViewRootForTest,View>>{
        active();check(Looper.myLooper()===Looper.getMainLooper())
        val out=mutableListOf<Pair<ViewRootForTest,View>>()
        fun originalOwner(view:View):Boolean{
            var context=view.context;var hops=0
            while(context !is android.app.Activity){
                if(hops++>=16)return false
                val base=(context as? ContextWrapper)?.baseContext?:return false
                if(base===context)return false;context=base
            }
            return context===this@C096Activity
        }
        fun visit(v:View,global:View){
            if(v is ViewRootForTest && originalOwner(v.view)){
                val provider=generateSequence(v.view){it.parent as? View}.filterIsInstance<DialogWindowProvider>().firstOrNull()
                val ownerWindow=provider?.window?:window
                val decor=ownerWindow.peekDecorView()
                if(decor!=null && global===decor && v.view.rootView===decor && decor.isAttachedToWindow && decor.isShown)
                    out+=v to decor
            }
            if(v is ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i),global)
        }
        // Public API29+: attached windows in this process only. Never inspect an IME/other app.
        WindowInspector.getGlobalWindowViews().filter{it.isAttachedToWindow && it.isShown}.forEach{visit(it,it)}
        return out
    }
    private fun nodes():List<OwnedNode>{
        val out=mutableListOf<OwnedNode>()
        fun visit(n:SemanticsNode,decor:View){out+=OwnedNode(n,decor);n.children.forEach{visit(it,decor)}}
        roots().forEach{(root,decor)->visit(root.semanticsOwner.unmergedRootSemanticsNode,decor)};return out
    }
    private fun selected(tag:String)=nodes().filter{it.node.config.getOrNull(SemanticsProperties.TestTag)==tag}
    private fun usableRect(decor:View):android.graphics.Rect{
        val insets=checkNotNull(decor.rootWindowInsets).getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
        val safe=android.graphics.Rect(insets.left,insets.top,decor.width-insets.right,decor.height-insets.bottom)
        val visible=android.graphics.Rect();if(!decor.getLocalVisibleRect(visible))return android.graphics.Rect()
        if(!safe.intersect(visible))return android.graphics.Rect();return safe
    }
    private fun visible(n:OwnedNode):Boolean{
        val b=n.node.boundsInWindow;val origin=IntArray(2);n.decor.getLocationInWindow(origin)
        val safe=usableRect(n.decor);val x=b.center.x-origin[0];val y=b.center.y-origin[1]
        return n.decor.isAttachedToWindow && n.decor.isShown && b.width>1 && b.height>1 &&
            x>=safe.left && x<safe.right && y>=safe.top && y<safe.bottom
    }
    private fun touch(decor:View,x:Float,y:Float){
        active();check(Looper.myLooper()===Looper.getMainLooper() && decor.isAttachedToWindow && decor.hasWindowFocus())
        val origin=IntArray(2);decor.getLocationInWindow(origin)
        val localX=x-origin[0];val localY=y-origin[1]
        val safe=usableRect(decor)
        check(localX>=safe.left && localX<safe.right && localY>=safe.top && localY<safe.bottom)
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,localX,localY,0)
            try{check(decor.dispatchTouchEvent(e))}finally{e.recycle()}
        }
    }
    private suspend fun swipe(decor:View,towardStart:Boolean=false){
        active();check(decor.isAttachedToWindow && decor.hasWindowFocus())
        val safe=usableRect(decor);check(safe.width()>1 && safe.height()>1);val t=SystemClock.uptimeMillis()
        for(i in 0..12){val a=when(i){0->MotionEvent.ACTION_DOWN;12->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE};if(i>0)delay(24)
            val y=safe.top+safe.height()*(if(towardStart).30f+i*.04f else .78f-i*.04f)
            val e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),a,safe.exactCenterX(),y,0)
            try{check(decor.dispatchTouchEvent(e))}finally{e.recycle()}}
    }
    private suspend fun tap(tag:String){
        await{selected(tag).singleOrNull()?.decor?.hasWindowFocus()==true}
        repeat(10){val n=selected(tag).single();if(visible(n)){
            check(SemanticsProperties.Disabled !in n.node.config);val p=n.node.boundsInWindow.center;touch(n.decor,p.x,p.y);delay(80);return
        };swipe(n.decor);delay(80)};error("C096_NODE_NOT_VISIBLE")
    }
    private suspend fun description(value:String){
        fun matches()=nodes().filter{it.node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value)==true && visible(it) && it.decor.hasWindowFocus()}
        await{matches().size==1};val n=matches().single();val p=n.node.boundsInWindow.center;touch(n.decor,p.x,p.y);delay(80)
    }
    private suspend fun focusedDecor():View{await{roots().map{it.second}.distinct().count{it.hasWindowFocus()}==1};return roots().map{it.second}.distinct().single{it.hasWindowFocus()}}
    private fun controlBounds(control:View,decor:View):List<Int>{
        val origin=IntArray(2);decor.getLocationOnScreen(origin)
        val at=IntArray(2);control.getLocationOnScreen(at)
        val safe=usableRect(decor).also{it.offset(origin[0],origin[1])}
        val bounds=android.graphics.Rect(at[0],at[1],at[0]+control.width,at[1]+control.height)
        val visible=android.graphics.Rect();check(control.getLocalVisibleRect(visible))
        check(control.isShown && control.isAttachedToWindow && bounds.width()>0 && bounds.height()>0 && safe.contains(bounds) && visible.contains(control.width/2,control.height/2))
        return listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)
    }
    private suspend fun addQaControl(control:View):View{
        val decor=focusedDecor();check(decor is android.widget.FrameLayout)
        val safe=usableRect(decor)
        val lp=android.widget.FrameLayout.LayoutParams(240,120,Gravity.TOP or Gravity.RIGHT)
        lp.setMargins(safe.left+16,safe.top+16,decor.width-safe.right+16,decor.height-safe.bottom+16)
        decor.addView(control,lp)
        await{control.isShown && control.width>0 && control.height>0};controlBounds(control,decor);return decor
    }
    private suspend fun reveal(tag:String,towardStart:Boolean=false):OwnedNode{
        await{selected(tag).singleOrNull()?.decor?.hasWindowFocus()==true}
        repeat(10){val node=selected(tag).single();if(visible(node))return node;swipe(node.decor,towardStart);delay(80)}
        error("C096_NODE_NOT_VISIBLE")
    }
    private suspend fun tapDay(date:java.time.LocalDate){
        // Same selector as DayCell and the existing native CalendarDetail consumer.
        // Clock remains REAL; a completed shift changes the tag, never the date.
        await{calendar.uiState.value.days.count{it.date==date}==1}
        val day=calendar.uiState.value.days.single{it.date==date}
        tap(if(day.isFullyCompleted)"completed-day-$date" else "day-$date")
    }
    private fun editorNodes(tag:String):List<OwnedNode>{
        val anchor=selected(tag).singleOrNull()?:return emptyList()
        val out=mutableListOf<OwnedNode>()
        fun visit(node:SemanticsNode){
            if(node.config.getOrNull(SemanticsProperties.EditableText)!=null)out+=OwnedNode(node,anchor.decor)
            node.children.forEach(::visit)
        }
        visit(anchor.node);return out
    }
    private fun editorFocused(tag:String):Boolean{
        val anchor=selected(tag).singleOrNull()?:return false
        fun focused(node:SemanticsNode):Boolean=node.config.getOrNull(SemanticsProperties.Focused)==true || node.children.any(::focused)
        return editorNodes(tag).size==1 && focused(anchor.node)
    }
    private fun editorText(tag:String)=editorNodes(tag).singleOrNull()?.node?.config?.getOrNull(SemanticsProperties.EditableText)?.text
    private fun inputObservation(role:String,tag:String,expected:String){
        assertionChecks[role+"_TAG_UNIQUE"]=selected(tag).size==1
        assertionChecks[role+"_EDITOR_UNIQUE"]=editorNodes(tag).size==1
        assertionChecks[role+"_FOCUSED"]=editorFocused(tag)
        assertionChecks[role+"_TEXT_EXACT"]=editorText(tag)==expected
    }
    private fun inputPoint(role:String,step:String,tag:String,expected:String){
        assertionPoint=role+"_"+step;inputObservation(role,tag,expected);checkpoint("CHECKED")
    }
    private fun inputGeometry(editor:OwnedNode):List<Int>{
        val origin=IntArray(2);editor.decor.getLocationInWindow(origin)
        val position=editor.node.positionInWindow;val size=editor.node.size;val clip=editor.node.boundsInWindow;val safe=usableRect(editor.decor)
        return listOf(editor.node.id,origin[0],origin[1],kotlin.math.round(position.x).toInt(),kotlin.math.round(position.y).toInt(),size.width,size.height,
            kotlin.math.round(clip.left).toInt(),kotlin.math.round(clip.top).toInt(),kotlin.math.round(clip.right).toInt(),kotlin.math.round(clip.bottom).toInt(),safe.left,safe.top,safe.right,safe.bottom)
    }
    private suspend fun hideOwnIme(referenceTag:String){
        check(!restored && variant==C096Variant.EDIT_DRAFT && phase=="DRAFT_INPUT")
        assertionPoint="NOTE_HIDE_IME";assertionChecks["NOTE_GEOMETRY_STABLE"]=false;checkpoint("CHECKED")
        val decor=focusedDecor()
        checkNotNull(getSystemService(android.view.inputmethod.InputMethodManager::class.java)).hideSoftInputFromWindow(decor.windowToken,0)
        var previous:List<Int>?=null;var stable=0
        withTimeout(5_000){while(stable<2){
            nextCloseFrame(decor);active()
            val hidden=decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==false
            assertionChecks["NOTE_IME_HIDDEN"]=hidden
            val editor=editorNodes(referenceTag).singleOrNull()
            val current=editor?.let(::inputGeometry)
            stable=if(hidden && current!=null && current==previous && !decor.isLayoutRequested && decor.hasWindowFocus())stable+1 else 0
            previous=current
        }}
        assertionChecks["NOTE_GEOMETRY_STABLE"]=true;checkpoint("CHECKED")
    }
    private fun noteFullyVisible(editor:OwnedNode):Boolean{
        val position=editor.node.positionInWindow;val size=editor.node.size;val clip=editor.node.boundsInWindow
        val origin=IntArray(2);editor.decor.getLocationInWindow(origin);val safe=usableRect(editor.decor)
        val left=position.x-origin[0];val top=position.y-origin[1];val right=left+size.width;val bottom=top+size.height
        return size.width>1 && size.height>1 && left>=safe.left && top>=safe.top && right<=safe.right && bottom<=safe.bottom &&
            clip.left<=position.x+1 && clip.top<=position.y+1 && clip.right>=position.x+size.width-1 && clip.bottom>=position.y+size.height-1
    }
    private suspend fun focusNote(tag:String,expected:String){
        check(!restored && variant==C096Variant.EDIT_DRAFT && phase=="DRAFT_INPUT")
        assertionChecks["NOTE_FOCUS_RETRIED"]=false
        withTimeout(12_000){repeat(3){attempt->
            if(attempt>0){assertionChecks["NOTE_FOCUS_RETRIED"]=true;hideOwnIme(tag)}
            inputPoint("NOTE","GEOMETRY",tag,expected);assertionChecks["NOTE_GEOMETRY_STABLE"]=false
            var previous:List<Int>?=null;var stable=0;var scrolls=0
            while(stable<2){
                active();val editor=editorNodes(tag).single()
                assertionChecks["NOTE_WINDOW_FOCUSED"]=editor.decor.hasWindowFocus()
                val full=noteFullyVisible(editor);assertionChecks["NOTE_FULLY_VISIBLE"]=full
                if(!full){
                    check(scrolls++<8);val origin=IntArray(2);editor.decor.getLocationInWindow(origin)
                    swipe(editor.decor,towardStart=editor.node.positionInWindow.y-origin[1]<usableRect(editor.decor).top)
                    previous=null;stable=0;delay(80)
                }else{
                    nextCloseFrame(editor.decor);val current=editorNodes(tag).single();val geometry=inputGeometry(current)
                    stable=if(noteFullyVisible(current) && current.decor.hasWindowFocus() && !current.decor.isLayoutRequested && geometry==previous)stable+1 else 0
                    previous=geometry
                }
            }
            assertionChecks["NOTE_GEOMETRY_STABLE"]=true;inputPoint("NOTE","HIT",tag,expected)
            val target=editorNodes(tag).single();check(noteFullyVisible(target))
            if(editorFocused(tag)){inputPoint("NOTE","FOCUS",tag,expected);return@withTimeout}
            check(SemanticsProperties.Disabled !in target.node.config)
            val point=target.node.positionInWindow;val size=target.node.size
            touch(target.decor,point.x+size.width/2f,point.y+size.height/2f)
            inputPoint("NOTE","FOCUS",tag,expected)
            val focused=withTimeoutOrNull(1_000){while(!editorFocused(tag)){inputObservation("NOTE",tag,expected);delay(32)};true}==true
            inputObservation("NOTE",tag,expected)
            if(focused)return@withTimeout
        };error("C096_NOTE_FOCUS_NOT_OBSERVED")}
    }
    private suspend fun type(role:String,tag:String,text:String){
        if(role=="NOTE"){
            focusNote(tag,text);inputPoint(role,"KEYS",tag,text)
            val keys=checkNotNull(KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()))
            keys.forEach{key->active();val target=editorNodes(tag).single();check(target.decor.hasWindowFocus() && editorFocused(tag))
                val consumed=target.decor.dispatchKeyEvent(key)
                if(key.action==KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(key.keyCode))check(consumed)
            }
            inputPoint(role,"TEXT",tag,text);await{inputObservation(role,tag,text);editorText(tag)==text}
            inputPoint(role,"COMPLETE",tag,text);return
        }
        inputPoint(role,"HIT",tag,text)
        await{inputObservation(role,tag,text);editorNodes(tag).size==1 && editorNodes(tag).single().decor.hasWindowFocus()}
        repeat(10){
            val editor=editorNodes(tag).single()
            if(visible(editor)){
                check(SemanticsProperties.Disabled !in editor.node.config)
                val point=editor.node.boundsInWindow.center;touch(editor.decor,point.x,point.y);delay(80)
                inputPoint(role,"FOCUS",tag,text)
                await{inputObservation(role,tag,text);editorFocused(tag)}
                inputPoint(role,"KEYS",tag,text)
                val keys=checkNotNull(KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()))
                keys.forEach{key->active();val target=editorNodes(tag).single();check(target.decor.hasWindowFocus() && editorFocused(tag))
                    val consumed=target.decor.dispatchKeyEvent(key)
                    if(key.action==KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(key.keyCode))check(consumed)
                }
                inputPoint(role,"TEXT",tag,text)
                await{inputObservation(role,tag,text);editorText(tag)==text}
                inputPoint(role,"COMPLETE",tag,text);return
            }
            swipe(editor.decor);delay(80)
        }
        error("C096_NODE_NOT_VISIBLE")
    }
    private suspend fun enterThroughUi(){
        stage("MONTH_INPUT"){val distance=java.time.temporal.ChronoUnit.MONTHS.between(calendar.uiState.value.visibleMonth,C096Recipe.month).toInt();check(kotlin.math.abs(distance)<=36);repeat(kotlin.math.abs(distance)){description(if(distance<0)"Mes anterior" else "Mes siguiente")};await{calendar.uiState.value.visibleMonth==C096Recipe.month}}
        if(variant==C096Variant.VIEW_DETAIL){stage("DETAIL_INPUT"){tapDay(C096Recipe.detailDate);await{calendar.uiState.value.detailDate==C096Recipe.detailDate}}}
        else {
            stage("MANUAL_INPUT"){
                tap("main-menu-button");tap("drawer-action-calendar-load")
                await{calendar.uiState.value.interactionMode==CalendarInteractionMode.EDIT && calendar.uiState.value.selectionPurpose==CalendarSelectionPurpose.MANUAL_LOAD && manual.uiState.value.stage==V2ManualShiftLoadStage.SELECT_DATES && manual.uiState.value.isActive}
                val selected=linkedSetOf<java.time.LocalDate>()
                C096Recipe.selectedDates.sorted().forEach{date->tapDay(date);selected+=date;await{calendar.uiState.value.editSelectedDates==selected}}
                tap("v2-manual-confirm-dates")
                await{manual.uiState.value.stage==V2ManualShiftLoadStage.CHOOSE_TEMPLATE && manual.uiState.value.selectedDates==C096Recipe.selectedDates && !manual.uiState.value.isLoading}
            }
            stage("TEMPLATE_INPUT"){tap("manual-institutions");tap("manual-place-"+fixture.placeId);tap("v2-template-"+fixture.templateId);await{manual.uiState.value.selectedTemplateId==fixture.templateId}}
            stage("DRAFT_INPUT"){
                assertionPoint="POSITION_EXPAND";checkpoint("CHECKED");tap("manual-toggle-position")
                type("POSITION","v2-manual-position",C096Recipe.POSITION)
                hideOwnIme("v2-manual-position")
                assertionPoint="NOTE_EXPAND";checkpoint("CHECKED");tap("manual-add-note")
                type("NOTE","manual-note",C096Recipe.NOTE)
                hideOwnIme("manual-note")
            }
            // A fixed private synthetic import, not OEM picker coverage. No draft is injected on B.
            stage("IMAGE_IMPORT"){
                C096NativeDiagnostics.beginImage(imageAttempt)
                val target=stableImageButton();imageEdge(C096NativeDiagnostics.ImageEdge.BUTTON_READY)
                val point=target.node.boundsInWindow.center
                imageEdge(C096NativeDiagnostics.ImageEdge.BUTTON_TOUCH);touch(target.decor,point.x,point.y)
                await{
                    imageAttempt.failure()?.let{throw C096NativeDiagnostics.ImageFailure(it)}
                    val state=manual.uiState.value
                    if(state.errorMessage!=null){imageEdge(C096NativeDiagnostics.ImageEdge.IMPORT_REJECTED);imageAttempt.failed("C096_IMAGE_IMPORT_REJECTED");throw C096NativeDiagnostics.ImageFailure("C096_IMAGE_IMPORT_REJECTED")}
                    if(state.isLoading)imageEdge(C096NativeDiagnostics.ImageEdge.IMPORT_PENDING)
                    if(!state.isLoading && state.noteAttachments.size==1){imageEdge(C096NativeDiagnostics.ImageEdge.ATTACHMENT_CONFIRMED);true}else false
                }
            }
        }
        stage("ASSERT_A"){assertState();record("state-a",snapshot())};readyA=true
        stage("BACKGROUND_INPUT"){
            // Framework input through the exact Activity's decor; no manual lifecycle callback.
            val control=android.widget.Button(this@C096Activity).apply{text="C096 fondo";setOnClickListener{active();check(!backgroundRequested);backgroundRequested=true;check(moveTaskToBack(true))}}
            val decor=addQaControl(control);val pos=IntArray(2);control.getLocationInWindow(pos);touch(decor,pos[0]+control.width/2f,pos[1]+control.height/2f)
        }
    }
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent:Intent,requestCode:Int,options:Bundle?) {
        active();check(!restored && phase=="IMAGE_IMPORT" && requestCode>=0)
        check(intent.action in setOf("android.provider.action.PICK_IMAGES","android.intent.action.OPEN_DOCUMENT","android.intent.action.GET_CONTENT"))
        check(intent.type=="image/*")
        // Only the picker boundary is QA-owned; the product launcher/result callback stays real.
        imageEdge(C096NativeDiagnostics.ImageEdge.LAUNCH_ENTERED)
        try{
            super.startActivityForResult(Intent(this,C096ImagePickerActivity::class.java).putExtra("variant",variant.name),requestCode,options)
            imageEdge(C096NativeDiagnostics.ImageEdge.LAUNCH_RETURNED)
        }catch(error:Throwable){imageAttempt.failed("C096_IMAGE_LAUNCH_FAILED");throw error}
    }
    private fun imageEdge(edge:C096NativeDiagnostics.ImageEdge){active();imageAttempt.reached(edge);checkpoint("CHECKED")}
    private fun receiveImageResult(uris:List<android.net.Uri>){
        try{
            active();check(!restored && variant==C096Variant.EDIT_DRAFT && phase=="IMAGE_IMPORT")
            check(!imageAttempt.observed(C096NativeDiagnostics.ImageEdge.CALLBACK_RECEIVED))
            imageEdge(C096NativeDiagnostics.ImageEdge.CALLBACK_RECEIVED)
            if(uris.isEmpty()){imageEdge(C096NativeDiagnostics.ImageEdge.RESULT_EMPTY);throw C096NativeDiagnostics.ImageFailure("C096_IMAGE_RESULT_EMPTY")}
            if(uris.size!=1 || uris.single()!=C096ImageProvider.uri(variant))throw C096NativeDiagnostics.ImageFailure("C096_IMAGE_RESULT_REJECTED")
            imageEdge(C096NativeDiagnostics.ImageEdge.URI_EXACT)
            imageEdge(C096NativeDiagnostics.ImageEdge.IMPORT_ENTERED)
            // Call the real ViewModel callback exactly once; never inject attachment/draft state.
            manual.importNoteImages(uris)
            imageEdge(C096NativeDiagnostics.ImageEdge.IMPORT_RETURNED)
            if(!manual.uiState.value.isLoading && manual.uiState.value.noteAttachments.isEmpty() && manual.uiState.value.errorMessage==null)
                throw C096NativeDiagnostics.ImageFailure("C096_IMAGE_IMPORT_NOT_STARTED")
        }catch(error:Throwable){
            imageAttempt.failed((error as? C096NativeDiagnostics.ImageFailure)?.code?:"C096_IMAGE_RESULT_REJECTED")
            fail(error)
        }
    }
    private suspend fun stableImageButton():OwnedNode{
        var previous:List<Int>?=null;var stable=0
        var target=reveal("manual-note-images")
        withTimeout(5_000){while(stable<2){
            active();target=selected("manual-note-images").single()
            if(!noteFullyVisible(target)){target=reveal("manual-note-images");previous=null;stable=0}
            nextCloseFrame(target.decor)
            target=selected("manual-note-images").single();val geometry=inputGeometry(target)
            stable=if(noteFullyVisible(target) && target.decor.hasWindowFocus() && !target.decor.isLayoutRequested && geometry==previous)stable+1 else 0
            previous=geometry
        }}
        check(SemanticsProperties.Disabled !in target.node.config);return target
    }
    private suspend fun nextCloseFrame(view:View)=suspendCancellableCoroutine<Unit>{continuation->
        val frame=Runnable{if(continuation.isActive)continuation.resumeWith(Result.success(Unit))}
        view.postOnAnimation(frame);continuation.invokeOnCancellation{view.removeCallbacks(frame)}
    }
    private suspend fun externalCloseTarget(control:View):List<Int>{
        val decor=focusedDecor()
        val content=checkNotNull(decor.findViewById<android.widget.FrameLayout>(android.R.id.content))
        check(content.rootView===decor && content.isAttachedToWindow)
        val decorScreen=IntArray(2);decor.getLocationOnScreen(decorScreen)
        val safe=usableRect(decor).also{it.offset(decorScreen[0],decorScreen[1])}
        val contentScreen=IntArray(2);content.getLocationOnScreen(contentScreen)
        val contentVisible=android.graphics.Rect();check(content.getLocalVisibleRect(contentVisible))
        contentVisible.offset(contentScreen[0],contentScreen[1]);check(safe.intersect(contentVisible))
        if(variant==C096Variant.VIEW_DETAIL){
            // Keep external input inside the actual displayed sheet, not its scrim.
            val body=selected("day-detail-scroll").single();check(body.decor===decor)
            val b=body.node.boundsInWindow;val origin=IntArray(2);decor.getLocationInWindow(origin)
            val displayed=android.graphics.Rect(kotlin.math.ceil(b.left).toInt()+decorScreen[0]-origin[0],kotlin.math.ceil(b.top).toInt()+decorScreen[1]-origin[1],kotlin.math.floor(b.right).toInt()+decorScreen[0]-origin[0],kotlin.math.floor(b.bottom).toInt()+decorScreen[1]-origin[1])
            check(safe.intersect(displayed))
        }
        check(safe.width()>=272 && safe.height()>=152)
        val lp=android.widget.FrameLayout.LayoutParams(240,120,Gravity.TOP or Gravity.LEFT)
        lp.leftMargin=safe.right-contentScreen[0]-256;lp.topMargin=safe.top-contentScreen[1]+16
        control.stateListAnimator=null;control.translationZ=0f
        control.elevation=(0 until content.childCount).maxOfOrNull{content.getChildAt(it).z}?.plus(1f)?:1f
        content.addView(control,lp);control.bringToFront()
        await{control.isShown && control.width>0 && control.height>0}
        var previous:List<Int>?=null;var stable=0
        withTimeout(30_000){
            while(stable<2){
                nextCloseFrame(decor);active()
                check(decor.hasWindowFocus() && content.rootView===decor && control.isEnabled && control.isClickable)
                val current=controlBounds(control,decor)
                val b=android.graphics.Rect(current[0],current[1],current[2],current[3])
                check(safe.contains(b) && (0 until content.childCount).all{content.getChildAt(it)===control || content.getChildAt(it).z<control.z})
                stable=if(current==previous && !decor.isLayoutRequested && !content.isLayoutRequested && !control.isLayoutRequested)stable+1 else 0
                previous=current
            }
        }
        assertionChecks["CLOSE_BOUNDS_STABLE"]=true
        return checkNotNull(previous)
    }
    private suspend fun restoreOnly(){
        stage("RESTORE_WAIT"){await{!manual.uiState.value.isLoading && !manual.uiState.value.isSaving && calendar.uiState.value.loadState==CalendarLoadState.CONTENT}}
        // B does not replay product input. Preserve the verified assertions/readback first.
        stage("ASSERT_B"){
            assertState();val observation=snapshot()
            assertionPoint="CLOSE_LAYOUT";checkpoint("CHECKED")
            val control=android.widget.Button(this@C096Activity).apply{text="C096 cerrar";setOnClickListener{
                active();phase="CLOSE_INPUT";checkpoint("CHECKED");finishAndRemoveTask()
            }}
            closeBounds=externalCloseTarget(control)
            assertionPoint="CLOSE_READY";checkpoint("CHECKED")
            record("state-b",observation.put("external_close_bounds",JSONArray(checkNotNull(closeBounds))))
        }
    }
    private suspend fun assertState(){
        active();assertionChecks.clear();assertionPoint="RENDERED"
        if(variant==C096Variant.VIEW_DETAIL){
            await{
                val sheet=selected("day-detail-modal-sheet").singleOrNull()
                assertionChecks["DETAIL_SHEET"]=sheet!=null && visible(sheet)
                assertionChecks["DETAIL_TEXT"]=sheet!=null && nodes().any{it.decor===sheet.decor && visible(it) && it.node.config.getOrNull(SemanticsProperties.Text)?.any{x->x.text.contains("Stage12-C096")}==true}
                assertionChecks.values.all{it}
            }
        }else{
            // Independent observations; native font/viewport need not fit both controls at once.
            assertionPoint="IMAGE_NODE";reveal("manual-note-images")
            assertionChecks["IMAGE_NODE"]=true
            assertionPoint="NOTE_NODE";val note=reveal("manual-note",towardStart=true)
            assertionChecks["NOTE_NODE"]=editorText("manual-note")==C096Recipe.NOTE
        }
        val c=calendar.uiState.value;val m=manual.uiState.value
        assertionChecks.putAll(linkedMapOf("MONTH" to (c.visibleMonth==C096Recipe.month),"CALENDAR_IDLE" to (!c.batchBusy && c.batchPreview==null),
            "MANUAL_IDLE" to (!m.isLoading && !m.isSaving && !m.saveAfterPreparation)))
        if(variant==C096Variant.VIEW_DETAIL){
            assertionChecks.putAll(linkedMapOf("VIEW_MODE" to (c.interactionMode==CalendarInteractionMode.VIEW),"DETAIL_DATE" to (c.detailDate==C096Recipe.detailDate),
                "EMPTY_EDIT" to c.editSelectedDates.isEmpty(),"NO_SELECTION_PURPOSE" to (c.selectionPurpose==null),"MANUAL_INACTIVE" to !m.isActive))
        }else{
            assertionChecks.putAll(linkedMapOf("EDIT_MODE" to (c.interactionMode==CalendarInteractionMode.EDIT),"NO_DETAIL" to (c.detailDate==null),
                "MANUAL_PURPOSE" to (c.selectionPurpose==CalendarSelectionPurpose.MANUAL_LOAD),"EDIT_DATES" to (c.editSelectedDates==C096Recipe.selectedDates),
                "OPERATION" to (m.operationId==fixture.operation),"TIMELINE" to (m.timelineId==fixture.timeline),"MANUAL_DATES" to (m.selectedDates==C096Recipe.selectedDates),
                "TEMPLATE" to (m.selectedTemplateId==fixture.templateId),"POSITION" to (m.position==C096Recipe.POSITION),"NOTE" to (m.noteBody==C096Recipe.NOTE),
                "NO_CREATION" to (m.creationRequest==null && m.creationResult==null),"NO_SUCCESS" to (m.successSequence==0),"MANUAL_STAGE" to (m.stage==V2ManualShiftLoadStage.CHOOSE_TEMPLATE),
                "NO_PENDING_DAY_OFF" to (m.pendingDayOff==null),"NO_OCCUPIED_POLICY" to (m.occupiedPolicy==null),"NO_OCCUPIED_DATES" to m.occupiedDates.isEmpty(),
                "NO_ATTEMPTED_DATES" to m.attemptedDates.isEmpty(),"NO_WARNINGS" to m.acknowledgedWarnings.isEmpty(),"NO_REVIEW" to (m.reviewFingerprint==null),
                "NO_PREVIEW" to (m.batchPreview==null),"NO_ERROR" to (m.errorMessage==null),"ONE_ATTACHMENT" to (m.noteAttachments.size==1)))
            val image=m.noteAttachments.singleOrNull()
            assertionChecks["IMAGE_METADATA"]=image!=null && image.noteId==fixture.operation && image.mimeType=="image/png" && image.byteSize==C096Recipe.png.size.toLong() && image.pixelWidth==1 && image.pixelHeight==1 && image.position==0
            assertionChecks["IMAGE_BYTES"]=image!=null && paths.file("files/manual_load_drafts/"+image.storageKey).readBytes().contentEquals(C096Recipe.png)
        }
        assertionPoint=assertionChecks.entries.firstOrNull{!it.value}?.key?:"COMPLETE"
        checkpoint("CHECKED");check(assertionChecks.values.all{it}){"C096_ASSERTION_FAILED"}
    }
    private fun projectedEditor(node:OwnedNode):SemanticsNode{
        val tag=node.node.config.getOrNull(SemanticsProperties.TestTag)
        return if(tag in setOf("manual-note","v2-manual-position"))checkNotNull(editorNodes(checkNotNull(tag)).singleOrNull()).node else node.node
    }
    private suspend fun snapshot():JSONObject {
        val read=withContext(Dispatchers.IO){C096FullStoreReadback.capture(paths).also(fixture::requireDatabase)}
        captureOrdinal++
        return identity().put("saved_state",JSONObject(handles.mapValues{(_,h)->JSONObject(h.keys().sorted().associateWith{key->jsonValue(h.get<Any?>(key))})}))
            .put("readback",readback(read)).put("events",JSONArray(events)).put("capture_ordinal",captureOrdinal)
            .put("no_autosave",!manual.uiState.value.isSaving && !manual.uiState.value.saveAfterPreparation && manual.uiState.value.successSequence==0)
            .put("initial_image_input",imageAttempt.initialInput(variant.run)).put("manual_operation_ids_created",manualIds).put("semantics",JSONArray(nodes().filter{visible(it)}.map{n->val field=projectedEditor(n);JSONObject().put("tag",n.node.config.getOrNull(SemanticsProperties.TestTag)?:JSONObject.NULL).put("text",JSONArray(n.node.config.getOrNull(SemanticsProperties.Text).orEmpty().map{it.text})).put("editable",field.config.getOrNull(SemanticsProperties.EditableText)?.text?:JSONObject.NULL).put("enabled",SemanticsProperties.Disabled !in field.config).put("bounds",JSONArray(listOf(n.node.boundsInWindow.left,n.node.boundsInWindow.top,n.node.boundsInWindow.right,n.node.boundsInWindow.bottom)))}))
    }
    // Closed synthetic diagnostics contain no exception messages, paths, rows or UI text.
    private fun failureCode(error:Throwable):String=imageAttempt.failure()?:when(error){
        is C096NativeDiagnostics.ImageFailure->imageAttempt.failure()?:error.code
        is android.content.pm.PackageManager.NameNotFoundException->"C096_PACKAGE_NOT_VISIBLE"
        is TimeoutCancellationException->"C096_HOST_TIMEOUT"
        is SecurityException->"C096_SECURITY_REJECTED"
        is IllegalStateException,is IllegalArgumentException->"C096_ASSERTION_FAILED"
        else->"C096_HOST_FAILED"
    }
    private fun diagnostic(edge:String,code:String)=JSONObject().put("schema","stage12-c096-diagnostic-v2")
        .put("program",C096Recipe.PROGRAM).put("run",variant.run).put("variant",variant.name)
        .put("uid",android.os.Process.myUid()).put("pid",android.os.Process.myPid())
        .put("generation",app.generation).put("runtime_api",Build.VERSION.SDK_INT).put("restored",restored)
        .put("phase",phase).put("first_phase",firstPhase?:JSONObject.NULL).put("edge",edge).put("code",code)
        .put("ready_a",readyA).put("background_requested",backgroundRequested).put("saved",saved).put("stopped",stopped)
        .put("events",JSONArray(events)).put("elapsed_ms",SystemClock.elapsedRealtime()-hostStarted).put("phase_timings",JSONArray(phaseTimings)).put("assertion_point",assertionPoint).put("assertion_checks",JSONObject(assertionChecks as Map<*,*>)).put("image_attempt",imageAttempt.snapshot())
    @Synchronized private fun checkpoint(edge:String,code:String="C096_OK"){
        // PATHS has no report directory yet on entry. Its first returned checkpoint is durable.
        if(!::paths.isInitialized || !paths.reportRoot.isDirectory)return
        paths.revalidate();val f=File(paths.reportRoot,"bootstrap.json")
        check(!java.nio.file.Files.isSymbolicLink(f.toPath()))
        java.nio.channels.FileChannel.open(f.toPath(),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,java.nio.file.StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS).use{channel->
            val bytes=java.nio.ByteBuffer.wrap(diagnostic(edge,code).toString().toByteArray(Charsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true)
        }
    }
    private fun clearCheckpoint(){paths.revalidate();val f=File(paths.reportRoot,"bootstrap.json");check(f.isFile && !java.nio.file.Files.isSymbolicLink(f.toPath()) && f.delete())}
    private fun identity()=JSONObject().put("schema","stage12-c096-observation-v1").put("program",C096Recipe.PROGRAM).put("run",variant.run).put("variant",variant.name).put("component",componentName.flattenToString()).put("task_id",taskId).put("uid",android.os.Process.myUid()).put("pid",android.os.Process.myPid()).put("generation",app.generation).put("process_start_elapsed",app.processStart).put("runtime_api",Build.VERSION.SDK_INT).put("qa_sha256",apkHash(C096Recipe.QA)).put("runner_sha256",apkHash(C096Recipe.QA+".test")).put("restored",restored).put("saved_state_present",restored).put("previous_generation",originalGeneration?:JSONObject.NULL).put("phase",phase)
    private val artifactHashes=mutableMapOf<String,String>()
    @Suppress("DEPRECATION") private fun apkHash(pkg:String):String = artifactHashes.getOrPut(pkg){
        check(pkg in setOf(C096Recipe.QA,C096Recipe.QA+".test"))
        val info=packageManager.getApplicationInfo(pkg,0);check(info.uid/100000==0)
        val md=java.security.MessageDigest.getInstance("SHA-256")
        File(info.sourceDir).inputStream().use{stream->val buffer=ByteArray(65536);while(true){val n=stream.read(buffer);if(n<0)break;md.update(buffer,0,n)}}
        md.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    private fun jsonValue(v:Any?):Any=when(v){null->JSONObject.NULL;is String,is Boolean,is Int,is Long,is Double,is Float->v;is List<*>->JSONArray(v.map(::jsonValue));is Array<*>->JSONArray(v.map(::jsonValue));else->error("C096_SAVED_VALUE_TYPE")}
    private fun readback(v:C096StoreSnapshot)=JSONObject().put("tables",JSONObject(v.tables.mapValues{(_,t)->JSONObject().put("columns",JSONArray(t.columns)).put("rows",JSONArray(t.rows.map{JSONArray(it)}))})).put("preferences",JSONObject(v.preferences)).put("files",JSONObject(v.files.mapValues{(_,f)->JSONObject().put("kind",f.kind.name).put("bytes",f.size).put("sha256",f.sha256)}))
    @Synchronized private fun record(name:String,value:JSONObject){check(name in setOf("state-a","saved","stopped","state-b","closed","failed"));paths.revalidate();val f=File(paths.reportRoot,name+".json");java.nio.channels.FileChannel.open(f.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE).use{channel->val bytes=java.nio.ByteBuffer.wrap(value.toString().toByteArray(Charsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true)}}
    override fun onSaveInstanceState(out:Bundle){
        out.putString("c096.program",C096Recipe.PROGRAM);out.putString("c096.variant",variant.name);out.putString("c096.generation",app.generation)
        super.onSaveInstanceState(out)
        if(!restored && readyA && backgroundRequested && !saved && first==null){saved=true;try{record("saved",identity().put("super_returned",true))}catch(e:Throwable){fail(e)}}
        if(first==null)try{checkpoint("SAVE_RETURNED")}catch(e:Throwable){fail(e)}
        recordBackgroundReadyAfterCallbacks()
    }
    override fun onStop(){
        super.onStop()
        if(!restored && readyA && backgroundRequested && !stopped && first==null)stopped=true
        if(first==null)try{checkpoint("STOP_RETURNED")}catch(e:Throwable){fail(e)}
        recordBackgroundReadyAfterCallbacks()
    }
    private fun recordBackgroundReadyAfterCallbacks(){
        // Android owns both callbacks; accept either order and publish only after both returned.
        if(restored || !readyA || !backgroundRequested || !saved || !stopped || backgroundReadyPosted || first!=null)return
        backgroundReadyPosted=true
        check(Handler(Looper.getMainLooper()).post{
            try{active();check(saved && stopped);record("stopped",identity().put("super_returned",true).put("saved_observed",true))}catch(e:Throwable){fail(e)}
        })
    }
    private fun fail(e:Throwable){retain(e);if(phase=="IMAGE_IMPORT"){(e as? C096NativeDiagnostics.ImageFailure)?.let{imageAttempt.failed(it.code)};imageAttempt.stop()};if(::paths.isInitialized && !emittedFailure){emittedFailure=true;val original=checkNotNull(first);try{checkpoint("FAILED",failureCode(original))}catch(_:Throwable){};try{record("failed",diagnostic("FAILED",failureCode(original)).put("status","FAILED"))}catch(_:Throwable){}};if(!isFinishing)finishAndRemoveTask()}
    override fun onDestroy(){
        super.onDestroy()
        try{composition?.observeDestroyed(this)}catch(e:Throwable){retain(e)}
        app.closer.launch {
            try{
                withTimeout(30_000){modelJobs.joinAll()};check(modelJobs.all{it.isCompleted})
                composition?.closeAfterDestroyed{f->val latch=java.util.concurrent.CountDownLatch(1);val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>();check(Handler(Looper.getMainLooper()).post{try{f()}catch(e:Throwable){failure.set(e)}finally{latch.countDown()}});check(latch.await(30,java.util.concurrent.TimeUnit.SECONDS));failure.get()?.let{throw it}}
                check(composition?.closureProven==true);store?.close();store=null
                if(restored && first==null){val rb=C096FullStoreReadback.capture(paths);fixture.requireDatabase(rb);paths.cleanupAfterClosure();closed=true;val closedIdentity=identity();clearCheckpoint();record("closed",closedIdentity.put("cleanup_complete",true).put("status","COMPLETE").put("owner_destroyed",isDestroyed).put("model_jobs_complete",true).put("composition_closed",true).put("store_closed",true).put("readback",readback(rb)))}
            }catch(e:Throwable){retain(e);val original=checkNotNull(first);try{checkpoint("FAILED",failureCode(original))}catch(_:Throwable){};try{record("failed",diagnostic("FAILED",failureCode(original)).put("status","FAILED"))}catch(_:Throwable){}}
        }
    }
}
