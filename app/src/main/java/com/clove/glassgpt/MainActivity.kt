package com.clove.glassgpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlassGPTScreen() }
    }
}
data class Message(val role: String, val text: String)

@Composable
fun GlassGPTScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pixelFont = remember {
        try {
            val file = File(context.cacheDir, "mojangles.otf")
            if (!file.exists()) context.assets.open("fonts/mojangles.otf").use { input -> file.outputStream().use(input::copyTo) }
            FontFamily(Font(file))
        } catch (_: Exception) { FontFamily.Default }
    }
    val prefs = remember { context.getSharedPreferences("glassgpt", 0) }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "gpt-4.1-mini") ?: "gpt-4.1-mini") }
    var settings by remember { mutableStateOf(key.isBlank()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(Message("assistant", "hey Clove :3\\nI'm GlassGPT. Add your API key in settings to chat.")) }
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build() }

    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFFC9B7FF), background=Color(0xFF10131D), surface=Color(0xFF202333))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF29233F), Color(0xFF10131D), Color(0xFF15302F))))) {
            Box(Modifier.offset((-70).dp, 100.dp).size(220.dp).blur(75.dp).background(Color(0x667E6BBD), CircleShape))
            Box(Modifier.align(Alignment.BottomEnd).offset(60.dp, (-90).dp).size(230.dp).blur(80.dp).background(Color(0x5562B9A7), CircleShape))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("GLASSGPT", fontFamily=pixelFont, fontSize=22.sp, color=Color.White)
                        Text("a little glass, a lot of brain", color=Color(0xFFB8B5CA), fontSize=11.sp)
                    }
                    IconButton(onClick={settings=true}) { Icon(Icons.Default.Settings, "Settings", tint=Color.White) }
                    IconButton(onClick={messages.clear(); messages.add(Message("assistant","fresh start. what are we building?"))}) { Icon(Icons.Default.Add,"New chat",tint=Color.White) }
                }
                Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0x26FFFFFF)).border(1.dp,Color(0x38FFFFFF),RoundedCornerShape(28.dp)).padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("CHAT",fontFamily=pixelFont,color=Color(0xFFC9B7FF),fontSize=12.sp)
                        Spacer(Modifier.weight(1f)); Text(model,color=Color.LightGray,fontSize=10.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(messages) { msg ->
                            val user=msg.role=="user"
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user) Arrangement.End else Arrangement.Start) {
                                Column(Modifier.widthIn(max=300.dp).clip(RoundedCornerShape(20.dp)).background(if(user) Color(0x447C70B6) else Color(0x22FFFFFF)).border(1.dp,Color(0x38FFFFFF),RoundedCornerShape(20.dp)).padding(13.dp)) {
                                    Text(if(user) "CLOVE" else "FOXGPT",fontFamily=pixelFont,fontSize=9.sp,color=if(user) Color(0xFFB8F2DF) else Color(0xFFC9B7FF))
                                    Spacer(Modifier.height(5.dp)); Text(msg.text,color=Color(0xFFF4F2FA),fontSize=14.sp,lineHeight=21.sp)
                                }
                            }
                        }
                        if(busy) item { Text("thinking...",fontFamily=pixelFont,color=Color(0xFFB8F2DF)) }
                    }
                    if(error.isNotBlank()) Text(error,color=Color(0xFFFF9EAA),fontSize=11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Color(0x33FFFFFF)).border(1.dp,Color(0x38FFFFFF),RoundedCornerShape(25.dp)).padding(start=16.dp,end=6.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    BasicTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f).padding(vertical=10.dp),textStyle=TextStyle(color=Color.White,fontSize=14.sp),decorationBox={ inner -> if(draft.isEmpty()) Text("Message GlassGPT...",color=Color.LightGray); inner() })
                    IconButton(enabled=draft.isNotBlank()&&!busy,onClick={
                        val prompt=draft.trim(); draft=""; error=""; messages.add(Message("user",prompt))
                        if(key.isBlank()){settings=true;return@IconButton}
                        busy=true
                        scope.launch {
                            try { messages.add(Message("assistant",withContext(Dispatchers.IO){callApi(client,key,model,messages.dropLast(1).toList()+Message("user",prompt))})) }
                            catch(e:Exception){error=e.message ?: "Request failed"}
                            finally{busy=false}
                        }
                    }) { Icon(Icons.Default.Send,"Send",tint=Color(0xFFC9B7FF)) }
                }
                Text("OpenAI API usage is billed separately · key stored on this device",color=Color(0xFF858397),fontSize=10.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=7.dp))
            }
        }
        if(settings) AlertDialog(
            onDismissRequest={if(key.isNotBlank()) settings=false},
            title={Text("GlassGPT settings",fontFamily=pixelFont)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("OpenAI API key"); OutlinedTextField(key,{key=it},singleLine=true,label={Text("sk-...")})
                Text("Model"); OutlinedTextField(model,{model=it},singleLine=true,label={Text("gpt-4.1-mini")})
                Text("Create a key at platform.openai.com. This prototype stores it locally. Don't share APKs containing your key.",fontSize=11.sp)
            }},
            confirmButton={TextButton(onClick={prefs.edit().putString("key",key.trim()).putString("model",model.trim()).apply();settings=false}){Text("Save")}}
        )
    }
}

private fun callApi(client:OkHttpClient,key:String,model:String,history:List<Message>):String {
    val input=JSONArray()
    history.takeLast(30).forEach{input.put(JSONObject().put("role",it.role).put("content",it.text))}
    val payload=JSONObject().put("model",model).put("input",input).toString().toRequestBody("application/json".toMediaType())
    val request=Request.Builder().url("https://api.openai.com/v1/responses").header("Authorization","Bearer $key").post(payload).build()
    client.newCall(request).execute().use { response ->
        val raw=response.body?.string().orEmpty()
        if(!response.isSuccessful) throw IllegalStateException("OpenAI ${response.code}: $raw")
        val output=JSONObject(raw).optJSONArray("output")?:JSONArray()
        val texts=mutableListOf<String>()
        for(i in 0 until output.length()){
            val content=output.optJSONObject(i)?.optJSONArray("content")?:continue
            for(j in 0 until content.length()){
                val part=content.optJSONObject(j)?:continue
                if(part.optString("type")=="output_text") texts.add(part.optString("text"))
            }
        }
        return texts.joinToString("\\n").ifBlank{"No text returned."}
    }
}
