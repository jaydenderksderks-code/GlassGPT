package com.clove.glassgpt

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
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
        setContent { GlassGPTApp() }
    }
}
data class ChatMessage(val role: String, val text: String)

private fun prefs(context: Context) = context.getSharedPreferences("glassgpt", Context.MODE_PRIVATE)
private fun readColor(value: String, fallback: Color): Color = try { Color(android.graphics.Color.parseColor(value)) } catch (_: Exception) { fallback }
private fun saveCopy(context: Context, uri: Uri, name: String): File? = try {
    val target = File(context.filesDir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
    target
} catch (_: Exception) { null }

@Composable
fun GlassGPTApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val p = remember { prefs(context) }
    var settingsOpen by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(p.getString("apiKey", "") ?: "") }
    var model by remember { mutableStateOf(p.getString("model", "gpt-4.1-mini") ?: "gpt-4.1-mini") }
    var accentHex by remember { mutableStateOf(p.getString("accent", "#C9B7FF") ?: "#C9B7FF") }
    var backgroundHex by remember { mutableStateOf(p.getString("background", "#11131D") ?: "#11131D") }
    var glass by remember { mutableStateOf(p.getBoolean("glass", true)) }
    var blurAmount by remember { mutableFloatStateOf(p.getFloat("blur", 28f)) }
    var opacity by remember { mutableFloatStateOf(p.getFloat("opacity", 0.18f)) }
    var corner by remember { mutableFloatStateOf(p.getFloat("corner", 26f)) }
    var fontSize by remember { mutableFloatStateOf(p.getFloat("fontSize", 15f)) }
    var compact by remember { mutableStateOf(p.getBoolean("compact", false)) }
    var showLabels by remember { mutableStateOf(p.getBoolean("labels", true)) }
    var wallpaperPath by remember { mutableStateOf(p.getString("wallpaper", "") ?: "") }
    var userAvatarPath by remember { mutableStateOf(p.getString("userAvatar", "") ?: "") }
    var assistantAvatarPath by remember { mutableStateOf(p.getString("assistantAvatar", "") ?: "") }
    var fontPath by remember { mutableStateOf(p.getString("fontPath", "") ?: "") }
    var bubbleStyle by remember { mutableStateOf(p.getString("bubble", "Glass") ?: "Glass") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(ChatMessage("assistant", "hey Clove :3\nWelcome to GlassGPT. Open settings to make this place yours.")) }
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build() }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveCopy(context, it, "custom-font.otf")?.let { file -> fontPath=file.absolutePath; p.edit().putString("fontPath",fontPath).apply() } }
    }
    val userAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveCopy(context, it, "user-avatar")?.let { file -> userAvatarPath=file.absolutePath; p.edit().putString("userAvatar",userAvatarPath).apply() } }
    }
    val assistantAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveCopy(context, it, "assistant-avatar")?.let { file -> assistantAvatarPath=file.absolutePath; p.edit().putString("assistantAvatar",assistantAvatarPath).apply() } }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveCopy(context, it, "custom-wallpaper")?.let { file -> wallpaperPath=file.absolutePath; p.edit().putString("wallpaper",wallpaperPath).apply() } }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> JSONObject(reader.readText()) }
                json?.let { j ->
                    accentHex=j.optString("accent",accentHex); backgroundHex=j.optString("background",backgroundHex)
                    glass=j.optBoolean("glass",glass); blurAmount=j.optDouble("blur",blurAmount.toDouble()).toFloat()
                    opacity=j.optDouble("opacity",opacity.toDouble()).toFloat(); corner=j.optDouble("corner",corner.toDouble()).toFloat()
                    fontSize=j.optDouble("fontSize",fontSize.toDouble()).toFloat(); compact=j.optBoolean("compact",compact)
                    showLabels=j.optBoolean("labels",showLabels); bubbleStyle=j.optString("bubble",bubbleStyle)
                    persistTheme(p,accentHex,backgroundHex,glass,blurAmount,opacity,corner,fontSize,compact,showLabels,bubbleStyle)
                }
            } catch (_: Exception) { error="Couldn't import that theme file." }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val json = themeJson(accentHex,backgroundHex,glass,blurAmount,opacity,corner,fontSize,compact,showLabels,bubbleStyle)
            runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(json.toString(2)) } }
        }
    }

    val accent = readColor(accentHex, Color(0xFFC9B7FF))
    val base = readColor(backgroundHex, Color(0xFF11131D))
    val customFont = remember(fontPath) {
        runCatching { if (fontPath.isNotBlank()) FontFamily(Font(File(fontPath))) else FontFamily.Default }.getOrDefault(FontFamily.Default)
    }
    val userAvatar = remember(userAvatarPath) { runCatching { if(userAvatarPath.isNotBlank()) BitmapFactory.decodeFile(userAvatarPath)?.asImageBitmap() else null }.getOrNull() }
    val assistantAvatar = remember(assistantAvatarPath) { runCatching { if(assistantAvatarPath.isNotBlank()) BitmapFactory.decodeFile(assistantAvatarPath)?.asImageBitmap() else null }.getOrNull() }
    val wallpaper = remember(wallpaperPath) {
        runCatching { if (wallpaperPath.isNotBlank()) BitmapFactory.decodeFile(wallpaperPath)?.asImageBitmap() else null }.getOrNull()
    }

    MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=base,surface=base.copy(.9f))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base,base.copy(red=(base.red*.72f).coerceIn(0f,1f)),Color(0xFF142C2B))))) {
            if (wallpaper != null) Image(wallpaper, null, Modifier.fillMaxSize(), contentScale=androidx.compose.ui.layout.ContentScale.Crop, alpha=.42f)
            Box(Modifier.offset((-80).dp,100.dp).size(240.dp).blur(if(glass) blurAmount.dp else 0.dp).background(accent.copy(alpha=.22f),CircleShape))
            Box(Modifier.align(Alignment.BottomEnd).offset(70.dp,(-70).dp).size(230.dp).blur(if(glass) blurAmount.dp else 0.dp).background(Color(0x5554C6A8),CircleShape))
            if (settingsOpen) {
                CustomizationScreen(
                    onBack={settingsOpen=false}, apiKey=apiKey,onApiKey={apiKey=it;p.edit().putString("apiKey",it).apply()},
                    model=model,onModel={model=it;p.edit().putString("model",it).apply()},
                    accentHex=accentHex,onAccent={accentHex=it;p.edit().putString("accent",it).apply()},
                    backgroundHex=backgroundHex,onBackground={backgroundHex=it;p.edit().putString("background",it).apply()},
                    glass=glass,onGlass={glass=it;p.edit().putBoolean("glass",it).apply()},
                    blur=blurAmount,onBlur={blurAmount=it;p.edit().putFloat("blur",it).apply()},
                    opacity=opacity,onOpacity={opacity=it;p.edit().putFloat("opacity",it).apply()},
                    corner=corner,onCorner={corner=it;p.edit().putFloat("corner",it).apply()},
                    fontSize=fontSize,onFontSize={fontSize=it;p.edit().putFloat("fontSize",it).apply()},
                    compact=compact,onCompact={compact=it;p.edit().putBoolean("compact",it).apply()},
                    labels=showLabels,onLabels={showLabels=it;p.edit().putBoolean("labels",it).apply()},
                    bubble=bubbleStyle,onBubble={bubbleStyle=it;p.edit().putString("bubble",it).apply()},
                    fontPath=fontPath,wallpaperPath=wallpaperPath,userAvatarPath=userAvatarPath,assistantAvatarPath=assistantAvatarPath,
                    onUserAvatar={userAvatarPicker.launch(arrayOf("image/*"))},onAssistantAvatar={assistantAvatarPicker.launch(arrayOf("image/*"))},
                    onFont={fontPicker.launch(arrayOf("font/ttf","font/otf","application/octet-stream"))},
                    onWallpaper={wallpaperPicker.launch(arrayOf("image/*"))},
                    onImport={importPicker.launch(arrayOf("application/json"))},
                    onExport={exportPicker.launch("glassgpt-theme.json")}
                )
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("GLASSGPT",fontFamily=customFont,fontSize=23.sp,color=Color.White)
                            Text("your space. your rules.",color=Color.White.copy(.65f),fontSize=11.sp)
                        }
                        IconButton(onClick={settingsOpen=true}) { Icon(Icons.Default.Tune,"Customize",tint=Color.White) }
                        IconButton(onClick={messages.clear();messages.add(ChatMessage("assistant","new chat. what are we making?"))}) { Icon(Icons.Default.Add,"New chat",tint=Color.White) }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(corner.dp))
                        .background(if(glass) Color.White.copy(opacity) else base.copy(.96f))
                        .border(1.dp,Color.White.copy(if(glass) .25f else .08f),RoundedCornerShape(corner.dp)).padding(if(compact) 9.dp else 15.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("CHAT",fontFamily=customFont,color=accent,fontSize=12.sp)
                            Spacer(Modifier.weight(1f)); Text(model,color=Color.White.copy(.55f),fontSize=10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 12.dp)) {
                            items(messages) { msg ->
                                val user=msg.role=="user"
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user) Arrangement.End else Arrangement.Start) {
                                    Column(Modifier.widthIn(max=310.dp).clip(RoundedCornerShape((corner*.72f).dp))
                                        .background(when(bubbleStyle){"Solid"->if(user) accent.copy(.32f) else Color.Black.copy(.22f);"Outline"->Color.Transparent;else->Color.White.copy(if(glass) opacity*.72f else .10f)})
                                        .then(if(bubbleStyle=="Outline") Modifier.border(1.dp,accent.copy(.55f),RoundedCornerShape((corner*.72f).dp)) else Modifier)
                                        .padding(if(compact) 9.dp else 13.dp)) {
                                        if(showLabels) Text(if(user) "YOU" else "GLASSGPT",fontFamily=customFont,fontSize=9.sp,color=if(user) accent else Color.White.copy(.68f))
                                        Spacer(Modifier.height(4.dp))
                                        Text(msg.text,color=Color.White,fontSize=fontSize.sp,lineHeight=(fontSize*1.48f).sp)
                                    }
                                }
                            }
                            if(busy) item { Text("thinking...",fontFamily=customFont,color=accent) }
                        }
                        if(error.isNotBlank()) Text(error,color=Color(0xFFFF9EAA),fontSize=11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(if(glass) opacity+.08f else .13f))
                        .border(1.dp,Color.White.copy(.2f),RoundedCornerShape(30.dp)).padding(start=16.dp,end=5.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        BasicTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f).padding(vertical=11.dp),
                            textStyle=TextStyle(color=Color.White,fontSize=fontSize.sp),decorationBox={ inner -> if(draft.isEmpty()) Text("Message GlassGPT...",color=Color.White.copy(.48f));inner() })
                        IconButton(enabled=draft.isNotBlank()&&!busy,onClick={
                            val prompt=draft.trim();draft="";error="";messages.add(ChatMessage("user",prompt))
                            if(apiKey.isBlank()){settingsOpen=true;return@IconButton}
                            busy=true
                            scope.launch {
                                try { val history=messages.toList(); val reply=withContext(Dispatchers.IO){callApi(client,apiKey,model,history)}
                                    messages.add(ChatMessage("assistant",reply))
                                } catch(e:Exception){error=e.message ?: "Request failed"} finally {busy=false}
                            }
                        }) { Icon(Icons.Default.Send,"Send",tint=accent) }
                    }
                    Text("OpenAI API billed separately · customize with the sliders above",color=Color.White.copy(.42f),fontSize=9.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=7.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomizationScreen(
    onBack:()->Unit, apiKey:String,onApiKey:(String)->Unit,model:String,onModel:(String)->Unit,
    accentHex:String,onAccent:(String)->Unit,backgroundHex:String,onBackground:(String)->Unit,
    glass:Boolean,onGlass:(Boolean)->Unit,blur:Float,onBlur:(Float)->Unit,opacity:Float,onOpacity:(Float)->Unit,
    corner:Float,onCorner:(Float)->Unit,fontSize:Float,onFontSize:(Float)->Unit,
    compact:Boolean,onCompact:(Boolean)->Unit,labels:Boolean,onLabels:(Boolean)->Unit,
    bubble:String,onBubble:(String)->Unit,fontPath:String,wallpaperPath:String,
    onFont:()->Unit,onWallpaper:()->Unit,userAvatarPath:String,assistantAvatarPath:String,onUserAvatar:()->Unit,onAssistantAvatar:()->Unit,onImport:()->Unit,onExport:()->Unit
) {
    val accent=readColor(accentHex,Color(0xFFC9B7FF))
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back",tint=Color.White)}
            Column(Modifier.weight(1f)){Text("MAKE IT YOURS",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("customization studio",fontSize=11.sp,color=Color.White.copy(.58f))}
            TextButton(onClick=onExport){Text("Export",color=accent)}
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=28.dp)) {
            item {
                SettingsCard("LIVE APPEARANCE") {
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Liquid Glass",Modifier.weight(1f),color=Color.White);Switch(glass,onGlass)}
                    SettingSlider("Glass blur",blur,0f,60f,"%.0f".format(blur)+" dp",onBlur)
                    SettingSlider("Panel transparency",opacity,.04f,.48f,"${(opacity*100).toInt()}%",onOpacity)
                    SettingSlider("Corner roundness",corner,8f,42f,"${corner.toInt()} dp",onCorner)
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Compact layout",Modifier.weight(1f),color=Color.White);Switch(compact,onCompact)}
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Show chat labels",Modifier.weight(1f),color=Color.White);Switch(labels,onLabels)}
                    Text("Bubble style",color=Color.White.copy(.7f),fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Glass","Solid","Outline").forEach{value->FilterChip(selected=bubble==value,onClick={onBubble(value)},label={Text(value)})}}
                }
            }
            item {
                SettingsCard("COLORS & MOOD") {
                    Text("Accent color",color=Color.White.copy(.7f),fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                        listOf("#C9B7FF","#F6E58D","#A7E8D0","#FF9EB5","#8DCBFF","#FFFFFF").forEach{hex->
                            Box(Modifier.size(34.dp).clip(CircleShape).background(readColor(hex,Color.White)).border(if(accentHex==hex) 3.dp else 1.dp,Color.White.copy(.8f),CircleShape).clickable{onAccent(hex)})
                        }
                    }
                    OutlinedTextField(accentHex,onAccent,label={Text("Custom accent HEX")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(backgroundHex,onBackground,label={Text("Background HEX")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("#11131D","#171A24","#1B2633","#2B2035","#30291D").forEach{hex->FilterChip(selected=backgroundHex==hex,onClick={onBackground(hex)},label={Text(hex)})}}
                }
            }
            item {
                SettingsCard("FONTS & UPLOADS") {
                    Text("Upload your own font (.ttf / .otf)",color=Color.White.copy(.75f),fontSize=12.sp)
                    Button(onClick=onFont,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FontDownload,null);Spacer(Modifier.width(8.dp));Text("Choose font file")}
                    Text(if(fontPath.isBlank()) "Current: built-in default font" else "Custom font loaded: "+File(fontPath).name,color=Color.White.copy(.55f),fontSize=11.sp)
                    SettingSlider("Chat text size",fontSize,11f,22f,"${fontSize.toInt()} sp",onFontSize)
                    Divider(color=Color.White.copy(.12f))
                    Text("Upload a wallpaper / background image",color=Color.White.copy(.75f),fontSize=12.sp)
                    OutlinedButton(onClick=onWallpaper,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("Choose image")}
                    Text(if(wallpaperPath.isBlank()) "No custom wallpaper" else "Wallpaper: "+File(wallpaperPath).name,color=Color.White.copy(.55f),fontSize=11.sp)\n                    OutlinedButton(onClick=onUserAvatar,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AccountCircle,null);Spacer(Modifier.width(8.dp));Text("Upload your avatar")}\n                    Text(if(userAvatarPath.isBlank()) "Default user avatar" else "User avatar: "+File(userAvatarPath).name,color=Color.White.copy(.55f),fontSize=11.sp)\n                    OutlinedButton(onClick=onAssistantAvatar,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.SmartToy,null);Spacer(Modifier.width(8.dp));Text("Upload assistant avatar")}\n                    Text(if(assistantAvatarPath.isBlank()) "Default assistant avatar" else "Assistant avatar: "+File(assistantAvatarPath).name,color=Color.White.copy(.55f),fontSize=11.sp)
                }
            }
            item {
                SettingsCard("AI CONNECTION") {
                    OutlinedTextField(apiKey,onApiKey,label={Text("OpenAI API key")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(model,onModel,label={Text("Model name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Text("API billing is separate from ChatGPT. Your key stays on this device in this prototype. Never include it in a shared APK.",color=Color.White.copy(.55f),fontSize=11.sp)
                }
            }
            item {
                SettingsCard("THEME PRESETS & BACKUP") {
                    Text("Export your settings as a JSON theme, or import one later.",color=Color.White.copy(.65f),fontSize=12.sp)
                    OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileUpload,null);Spacer(Modifier.width(8.dp));Text("Export theme")}
                    OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileDownload,null);Spacer(Modifier.width(8.dp));Text("Import theme JSON")}
                }
            }
            item { Text("GlassGPT customization • changes apply immediately",color=Color.White.copy(.42f),fontSize=10.sp,modifier=Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun SettingsCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.10f)).border(1.dp,Color.White.copy(.17f),RoundedCornerShape(24.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(title,color=Color(0xFFC9B7FF),fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.sp)
        content()
    }
}
@Composable
private fun SettingSlider(label:String,value:Float,min:Float,max:Float,valueText:String,onChange:(Float)->Unit) {
    Column {
        Row(verticalAlignment=Alignment.CenterVertically){Text(label,color=Color.White.copy(.82f),fontSize=12.sp,modifier=Modifier.weight(1f));Text(valueText,color=Color.White.copy(.55f),fontSize=11.sp)}
        Slider(value=value.coerceIn(min,max),onValueChange=onChange,valueRange=min..max)
    }
}
private fun persistTheme(p:android.content.SharedPreferences,accent:String,bg:String,glass:Boolean,blur:Float,opacity:Float,corner:Float,fontSize:Float,compact:Boolean,labels:Boolean,bubble:String) {
    p.edit().putString("accent",accent).putString("background",bg).putBoolean("glass",glass).putFloat("blur",blur)
        .putFloat("opacity",opacity).putFloat("corner",corner).putFloat("fontSize",fontSize)
        .putBoolean("compact",compact).putBoolean("labels",labels).putString("bubble",bubble).apply()
}
private fun themeJson(accent:String,bg:String,glass:Boolean,blur:Float,opacity:Float,corner:Float,fontSize:Float,compact:Boolean,labels:Boolean,bubble:String)=
    JSONObject().put("accent",accent).put("background",bg).put("glass",glass).put("blur",blur).put("opacity",opacity)
        .put("corner",corner).put("fontSize",fontSize).put("compact",compact).put("labels",labels).put("bubble",bubble)

private fun callApi(client:OkHttpClient,key:String,model:String,history:List<ChatMessage>):String {
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
        return texts.joinToString("\n").ifBlank{"No text returned."}
    }
}
