package com.raunak.daytimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

data class Task(val id:Long=System.nanoTime(),val name:String,val start:Int,val end:Int,val done:Boolean=false,val pomodoro:Boolean=false)

private fun fmt(x:Int)=String.format("%02d:%02d",x/60,x%60)
private fun parse(x:String)=x.split(":").let{(it.getOrNull(0)?.toIntOrNull()?:0)*60+(it.getOrNull(1)?.toIntOrNull()?:0)}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{DayTheme{TimelineApp()}}}}

@Composable fun DayTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF5B4BFF),onPrimary=Color.White,primaryContainer=Color(0xFFE8E5FF),secondary=Color(0xFF00A99D),secondaryContainer=Color(0xFFD6F7F2),surface=Color(0xFFF8F8FC),surfaceVariant=Color(0xFFEDEDF4),background=Color(0xFFF8F8FC))){content()}}

@Composable fun TimelineApp(){
 var tasks by remember{mutableStateOf(listOf(Task(name="Deep Work",start=9*60,end=10*60+30,pomodoro=true),Task(name="Lunch",start=13*60,end=14*60),Task(name="DSA Practice",start=18*60,end=20*60,pomodoro=true)))}
 var add by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<Task?>(null)}
 val today=SimpleDateFormat("EEE, dd MMM",Locale.getDefault()).format(Date())
 Scaffold(containerColor=Color(0xFFF8F8FC),topBar={
  Column(Modifier.fillMaxWidth().background(Color(0xFFF8F8FC)).padding(horizontal=20.dp,vertical=16.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("My Day",fontSize=29.sp,fontWeight=FontWeight.Bold);Text(today,fontSize=13.sp,color=Color.Gray)}
    Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFFE8E5FF),modifier=Modifier.clickable{add=true}){Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Add,null,tint=Color(0xFF5B4BFF));Spacer(Modifier.width(5.dp));Text("Task",color=Color(0xFF4A3FE0),fontWeight=FontWeight.SemiBold)}}}
  }
 },floatingActionButton={FloatingActionButton(onClick={add=true},containerColor=Color(0xFF5B4BFF),contentColor=Color.White){Icon(Icons.Default.Add,"Add")}}){p->
  LazyColumn(Modifier.padding(p).fillMaxSize(),contentPadding=PaddingValues(bottom=100.dp,top=4.dp)){
   item{CurrentFocus(tasks)}
   items((0..23).toList()){h->HourRow(h,tasks.filter{it.start<h*60+60&&it.end>h*60},onClick={add=true},onTask={selected=it})}
  }
 }
 if(add)AddDialog({add=false}){tasks=(tasks+it).sortedBy{t->t.start};add=false}
 selected?.let{TaskDialog(it,{selected=null},{tasks=tasks.map{t->if(t.id==it.id)t.copy(done=!t.done)else t};selected=null},{tasks=tasks.filterNot{t->t.id==it.id};selected=null})}
}

@Composable fun CurrentFocus(tasks:List<Task>){val now=Calendar.getInstance();val m=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);val active=tasks.firstOrNull{m>=it.start&&m<it.end};Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFF17162A),modifier=Modifier.padding(horizontal=16.dp,vertical=10.dp).fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF7C6CFF)));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("NOW",fontSize=11.sp,color=Color(0xFFAAA5C8),fontWeight=FontWeight.Bold);Text(active?.name?:"No active task",fontSize=18.sp,color=Color.White,fontWeight=FontWeight.SemiBold)};Text(fmt(m),color=Color.White,fontSize=13.sp)}}}

@Composable fun HourRow(h:Int,tasks:List<Task>,onClick:()->Unit,onTask:(Task)->Unit){Row(Modifier.fillMaxWidth().height(88.dp).padding(horizontal=12.dp)){
 Box(Modifier.width(52.dp).fillMaxHeight(),contentAlignment=Alignment.TopCenter){Text(fmt(h*60),fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(top=2.dp))}
 Box(Modifier.weight(1f).fillMaxHeight().border(1.dp,Color(0xFFE5E4EC)).clickable{onClick()}){tasks.filter{it.start/60==h}.forEach{t->TaskCard(t,onTask)}}}}

@Composable fun TaskCard(t:Task,onClick:(Task)->Unit){val c=if(t.done)Color(0xFFE4E4EA) else if(t.pomodoro)Color(0xFFDCD8FF) else Color(0xFFD7F3EE);Surface(color=c,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(5.dp).clickable{onClick(t)}){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if(t.pomodoro)Color(0xFF5B4BFF) else Color(0xFF00A99D)));Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(if(t.done)"✓ ${t.name}" else t.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp);Text("${fmt(t.start)} – ${fmt(t.end)}",fontSize=11.sp,color=Color.DarkGray)};if(t.pomodoro)Icon(Icons.Default.Timer,null,modifier=Modifier.size(17.dp),tint=Color(0xFF5B4BFF))}}}

@Composable fun AddDialog(close:()->Unit,add:(Task)->Unit){var n by remember{mutableStateOf("")};var s by remember{mutableStateOf("09:00")};var e by remember{mutableStateOf("10:00")};var p by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=close,title={Text("Plan a task",fontWeight=FontWeight.Bold)},text={Column{OutlinedTextField(n,{n=it},label={Text("What are you doing?")},singleLine=true);Spacer(Modifier.height(8.dp));Row{OutlinedTextField(s,{s=it},Modifier.weight(1f),label={Text("Start")},singleLine=true);Spacer(Modifier.width(8.dp));OutlinedTextField(e,{e=it},Modifier.weight(1f),label={Text("End")},singleLine=true)};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(p,{p=it});Text("Enable Pomodoro")}}},confirmButton={Button(onClick={if(n.isNotBlank()&&parse(e)>parse(s))add(Task(name=n,start=parse(s),end=parse(e),pomodoro=p))}){Text("Add task")}},dismissButton={TextButton(close){Text("Cancel")}})}

@Composable fun TaskDialog(t:Task,close:()->Unit,toggle:()->Unit,delete:()->Unit){AlertDialog(onDismissRequest=close,title={Text(t.name,fontWeight=FontWeight.Bold)},text={Column{Text("${fmt(t.start)} – ${fmt(t.end)}");if(t.pomodoro)Text("Pomodoro enabled",color=Color(0xFF5B4BFF));if(t.done)Text("Completed",color=Color(0xFF00A99D))}},confirmButton={TextButton(toggle){Text(if(t.done)"Mark active" else "Complete")}},dismissButton={Row{TextButton(delete){Text("Delete")};TextButton(close){Text("Close")}}})}
