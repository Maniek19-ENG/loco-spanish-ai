package com.locospanish.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.locospanish.ai.model.VoicePhase
import kotlin.math.*
import kotlin.random.Random
import android.os.SystemClock
import kotlinx.coroutines.delay

private data class MeshPoint(val x:Float,val y:Float,val z:Float,val seed:Float)
private data class MeshEdge(val a:Int,val b:Int)
private data class OrbMesh(val nodes:List<MeshPoint>,val edges:List<MeshEdge>)
private data class Projected(val point:Offset,val depth:Float)
private fun createMesh():OrbMesh {
    val random=Random(237)
    val nodes=List(146) { i ->
        val y=1f-2f*(i+.5f)/146f
        val a=i*2.3999632f+random.nextFloat()*.42f
        val ring=sqrt(1f-y*y)
        val radius=if(i%11==0) .64f else .92f+random.nextFloat()*.09f
        MeshPoint(cos(a)*ring*radius,y*radius,sin(a)*ring*radius,random.nextFloat()*6.28f)
    }
    val edges=mutableSetOf<Pair<Int,Int>>()
    nodes.forEachIndexed { i,p ->
        nodes.indices.filter { it!=i }.sortedBy { j ->
            val q=nodes[j];(p.x-q.x).pow(2)+(p.y-q.y).pow(2)+(p.z-q.z).pow(2)
        }.take(if(i%7==0) 5 else 4).forEach { j -> edges.add(min(i,j) to max(i,j)) }
    }
    return OrbMesh(nodes,edges.map { MeshEdge(it.first,it.second) })
}

@Composable internal fun VoiceOrb(phase:VoicePhase, modifier:Modifier=Modifier, audioLevel:Float=0f) {
    val mesh=remember { createMesh() }
    var time by remember { mutableFloatStateOf(0f) }
    var loudness by remember { mutableFloatStateOf(0f) }
    val latestLevel by rememberUpdatedState(audioLevel.coerceIn(0f,1f))
    LaunchedEffect(Unit) {
        val started=SystemClock.elapsedRealtime()
        while(true) {
            time=((SystemClock.elapsedRealtime()-started)%24000L)/24000f*(2*PI).toFloat()
            loudness+=(latestLevel-loudness)*.45f
            delay(33)
        }
    }
    val color by animateColorAsState(when(phase) {
        VoicePhase.SPEAKING -> Color(0xFF30F997)
        VoicePhase.ERROR -> Color(0xFFFF4D64)
        else -> Color(0xFF31BBFF)
    },tween(380),label="voiceColor")
    val movement by animateFloatAsState(if(phase==VoicePhase.SPEAKING) 1f else .35f,tween(420),label="motion")
    val description=when(phase) { VoicePhase.SPEAKING -> "LOCO mówi — zielona kula"; VoicePhase.ERROR -> "Błąd — czerwona kula"; else -> "LOCO słucha lub czeka — niebieska kula" }
    Box(modifier.fillMaxWidth().height(330.dp).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(310.dp)) {
            val center=Offset(size.width/2,size.height/2)
            val radius=size.minDimension*.405f*(.78f+loudness*.28f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.10f),color.copy(alpha=.025f),Color.Transparent),center,radius*1.3f),radius*1.3f,center)
            val yaw=time;val tilt=.35f+sin(time)*.22f
            val points=mesh.nodes.map { p ->
                val ripple=1f+sin(time*4f+p.seed)*.035f*movement+sin(time*7f+p.y*5f)*.018f*movement
                val x=(p.x*cos(yaw)+p.z*sin(yaw))*ripple
                val z=(-p.x*sin(yaw)+p.z*cos(yaw))*ripple
                val y=p.y*ripple
                val py=y*cos(tilt)-z*sin(tilt)
                val depth=y*sin(tilt)+z*cos(tilt)
                val perspective=2.9f/(2.9f-depth*.28f)
                Projected(Offset(center.x+x*radius*perspective,center.y+py*radius*perspective),depth)
            }
            mesh.edges.forEachIndexed { i,e ->
                val a=points[e.a];val b=points[e.b]
                val front=((a.depth+b.depth)*.25f+.5f).coerceIn(0f,1f)
                val shimmer=(sin(time*3f+i*1.7f)*.5f+.5f)
                val alpha=.08f+front*.32f+shimmer*.16f
                if(front>.6f && shimmer>.75f) drawLine(color.copy(alpha=alpha*.12f),a.point,b.point,3.5.dp.toPx(),StrokeCap.Round)
                drawLine(color.copy(alpha=alpha),a.point,b.point,(.35f+front*.6f).dp.toPx(),StrokeCap.Round)
            }
            points.forEachIndexed { i,p ->
                val front=(p.depth*.5f+.5f).coerceIn(0f,1f)
                val bright=(sin(time*4f+mesh.nodes[i].seed)*.5f+.5f)
                val dot=(.45f+front*.65f+bright*.35f).dp.toPx()
                if(front>.65f && bright>.7f) drawCircle(color.copy(alpha=.10f),dot*4.5f,p.point)
                drawCircle(color.copy(alpha=.22f+front*.58f),dot,p.point)
                if(bright>.93f && front>.65f) drawCircle(Color.White.copy(alpha=.85f),dot*.55f,p.point)
            }
        }
    }
}
