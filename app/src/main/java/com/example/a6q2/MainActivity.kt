package com.example.a6q2

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.a6q2.ui.theme.A6Q2Theme
import kotlin.math.PI
import kotlin.math.roundToInt

// Main screen wires up sensors to a simple compass + level UI
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var magnet: Sensor? = null
    private var gyro: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMag = false

    private var lastGyroTime = 0L
    private var rollRad = 0f
    private var pitchRad = 0f

    private var headingDeg by mutableStateOf(0f)
    private var rollDeg by mutableStateOf(0f)
    private var pitchDeg by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            A6Q2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                    CompassLevelScreen(
                        heading = headingDeg,
                        roll = rollDeg,
                        pitch = pitchDeg
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accel?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnet?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Update compass + level when sensors fire
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                updateHeading()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasMag = true
                updateHeading()
            }
            Sensor.TYPE_GYROSCOPE -> {
                val now = event.timestamp
                if (lastGyroTime != 0L) {
                    val dt = (now - lastGyroTime) / 1_000_000_000f
                    rollRad += event.values[0] * dt
                    pitchRad += event.values[1] * dt
                    rollDeg = toDegrees(rollRad)
                    pitchDeg = toDegrees(pitchRad)
                }
                lastGyroTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateHeading() {
        if (!hasGravity || !hasMag) return
        val R = FloatArray(9)
        val I = FloatArray(9)
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (az < 0) az += 360f
            headingDeg = az
        }
    }

    private fun toDegrees(rad: Float): Float = rad * 180f / PI.toFloat()
}

@Composable
fun CompassLevelScreen(heading: Float, roll: Float, pitch: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Compass", color = Color(0xFFFFD166), style = MaterialTheme.typography.headlineMedium)
        CompassCard(heading)

        Spacer(modifier = Modifier.height(12.dp))

        Text("Digital Level", color = Color(0xFF4BE1EC), style = MaterialTheme.typography.headlineMedium)
        LevelCard(roll = roll, pitch = pitch)
    }
}

@Composable
fun CompassCard(heading: Float) {
    Card(
        modifier = Modifier
            .size(260.dp)
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2
                drawCircle(Color(0xFFFFD166), radius, center, style = Stroke(6f))
                drawCircle(Color(0xFF4BE1EC), radius * 0.08f, center)
                drawCircle(Color(0xFF4BE1EC), radius * 0.85f, center, style = Stroke(3f))
            }

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .rotate(-heading)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val needleLen = size.minDimension / 2 * 0.9f
                    drawLine(
                        color = Color(0xFFEF476F),
                        start = center,
                        end = Offset(center.x, center.y - needleLen),
                        strokeWidth = 12f
                    )
                    drawLine(
                        color = Color(0xFF06D6A0),
                        start = center,
                        end = Offset(center.x, center.y + needleLen),
                        strokeWidth = 12f
                    )
                }
            }

            Text(
                text = "${heading.roundToInt()}°",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
fun LevelCard(roll: Float, pitch: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LevelRow(label = "Roll", value = roll, barColor = Color(0xFFFFA500))
            LevelRow(label = "Pitch", value = pitch, barColor = Color(0xFF9B59B6))
        }
    }
}

@Composable
fun LevelRow(label: String, value: Float, barColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$label: ${value.roundToInt()}°", color = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color(0xFF0B1220), shape = CircleShape)
        ) {
            val clamped = value.coerceIn(-90f, 90f)
            val percent = (clamped + 90f) / 180f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .background(barColor, shape = CircleShape)
            )
        }
    }
}
