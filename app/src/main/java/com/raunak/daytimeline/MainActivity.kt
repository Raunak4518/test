package com.raunak.daytimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Timeline() {
    var tasks by remember { mutableStateOf(listOf<Task>()) }
    var dialog by remember { mutableStateOf(false) }
    Scaffold(topBar={TopAppBar(title={Column{Text("Today");Text(SimpleDateFormat("EEE, dd MMM",Locale.getDefault()).format(Date()),fontSize=12.sp)}})}, floatingActionButton={FloatingActionButton({dialog=true}){Text("+")}}) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=90.dp)) {
            for(h in 0..23) Row(Modifier.height(92.dp).fillMaxWidth()) {
                Box(Modifier.width(58.dp).fillMaxHeight(),contentAlignment=Alignment.TopCenter){Text(String.format("%02d:00",h),fontSize=11.sp)}
                Box(Modifier.weight(1f).fillMaxHeight().clickable{dialog=true}) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.12f))){ }
                    tasks.filter{it.start<h*60+60&&it.end>h*60}.forEach { t ->
                        if(t.start/60==h) Box(Modifier.fillMaxWidth().height(((t.end-t.start)/60f*92f).coerceAtLeast(44f).dp).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp)) { Column { Text(t.name); Text("${fmt(t.start)} – ${fmt(t.end)}",fontSize=11.sp) } }
                    }
                }
            }
        }
    }
    if(dialog) AddDialog({dialog=false}) { tasks=(tasks+it).sortedBy{t->t.start}; dialog=false }
}

data class Task(val name:String,val start:Int,val end:Int)
fun fmt(x:Int)=String.format("%02d:%02d",x/60,x%60)
fun parse(x:String)=x.split(":").let{(it.getOrNull(0)?.toIntOrNull()?:0)*60+(it.getOrNull(1)?.toIntOrNull()?:0)}

@Composable fun AddDialog(close:()->Unit, add:(Task)->Unit) {
    var n by remember{mutableStateOf("")}; var s by remember{mutableStateOf("09:00")}; var e by remember{mutableStateOf("10:00")}
    AlertDialog(onDismissRequest=close,title={Text("Add task")},text={Column{OutlinedTextField(n,{n=it},label={Text("Name")});OutlinedTextField(s,{s=it},label={Text("Start HH:MM")});OutlinedTextField(e,{e=it},label={Text("End HH:MM")})}},confirmButton={TextButton({if(n.isNotBlank()&&parse(e)>parse(s))add(Task(n,parse(s),parse(e)))}){Text("Create")}},dismissButton={TextButton(close){Text("Cancel")}})
}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme{Timeline()}}}}
