package com.example.pythonOnAndroid.activities.game

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pythonOnAndroid.gameObjects.Food
import com.example.pythonOnAndroid.Helper
import com.example.pythonOnAndroid.R
import com.example.pythonOnAndroid.gameObjects.Snake
import com.example.pythonOnAndroid.activities.MenuActivity
import com.example.pythonOnAndroid.PreferenceKeys
import com.example.pythonOnAndroid.databinding.ActivityGameBinding
import com.example.pythonOnAndroid.db.ScoreDatabase
import com.example.pythonOnAndroid.db.ScoreEntity
import com.example.pythonOnAndroid.gameObjects.Directions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class GameActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityGameBinding
    private lateinit var sensorManager: SensorManager
    private var movementSensitivity: Float = 2F
    private var score: Float = 0F
    private var scoreMultiplier: Float = 0F
    private lateinit var addGameFinishDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Snake.alive = true
        binding = ActivityGameBinding.inflate(layoutInflater)
        binding.scoreTextView.text = resources.getString(R.string.score_place_holder_txt).format(0F)
        val sharedPref = getSharedPreferences(PreferenceKeys.preferenceName, MODE_PRIVATE)
        movementSensitivity = sharedPref.getFloat(PreferenceKeys.sensibility, 2F)
        scoreMultiplier = sharedPref.getFloat(PreferenceKeys.scoreMultiplier, 1F)
        binding.scoreMultiplier.text =
            resources.getString(R.string.score_multiplier).format(scoreMultiplier)
        addGameFinishDialog = AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.game_over_dialog_title))
            .setNegativeButton(resources.getString(R.string.game_over_dialog_back_to_menu)) { _, _ ->
                quitGame()
            }.create()

        setContentView(binding.root)
        setUpSensor()
        moveSnake(sharedPref)
    }

    private fun moveSnake(sharedPref: SharedPreferences) {
        CoroutineScope(Dispatchers.IO).launch {
            while (Snake.alive) {
                moveSnakeDirection(sharedPref)
                Snake.bodyParts.add(arrayOf(Snake.headX, Snake.headY))
                if (Snake.headX == Food.posX && Snake.headY == Food.posY) {
                    updateScore(score + (1 * scoreMultiplier))
                    Food.generate()
                } else {
                    Snake.bodyParts.removeAt(0)
                }
                binding.canvas.invalidate()
                delay(sharedPref.getLong(PreferenceKeys.snakeSpeed, 150L))
            }
        }
    }

    private fun moveSnakeDirection(sharedPref: SharedPreferences) {
        when (Snake.direction) {
            Directions.UP -> {
                Snake.headY -= 50
                checkPossibleMoves(sharedPref)
            }
            Directions.DOWN -> {
                Snake.headY += 50
                checkPossibleMoves(sharedPref)
            }
            Directions.LEFT -> {
                Snake.headX -= 50
                checkPossibleMoves(sharedPref)
            }
            Directions.RIGHT -> {
                Snake.headX += 50
                checkPossibleMoves(sharedPref)
            }
        }
    }

    private fun checkPossibleMoves(sharedPref: SharedPreferences) {
        if (!Snake.possibleMove()) {
            Snake.alive = false
            Snake.reset()
            if (Helper.isAppOnline(applicationContext)) {
                updateScoreOnFireBase()
            } else {
                val currentScore = score
                val userName =
                    sharedPref.getString(PreferenceKeys.userName, "Unknown User").toString()
                val dao = ScoreDatabase.getInstance(this).scoreDao
                lifecycleScope.launch {
                    dao.insert(
                        ScoreEntity(
                            userName,
                            Helper.roundToTwoDecimals(currentScore.toDouble())
                        )
                    )
                }
            }
            if (!isFinishing) {
                runOnUiThread {
                    addGameFinishDialog.setMessage(
                        resources.getString(R.string.game_over_dialog_score).format(score)
                    )
                    addGameFinishDialog.show()
                }
            }
            binding.canvas.scaleX = 0F
            runOnUiThread {
                binding.scoreTextView.text = ""
                binding.scoreMultiplier.text = ""
            }
        }
    }

    private fun updateScoreOnFireBase() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && !user.isAnonymous) {
            val url = getString(R.string.dbURL)
            val reference = FirebaseDatabase.getInstance(url).getReference("leaderboard")
            reference.child(user.displayName.toString()).get().addOnSuccessListener {
                val finalScore = ((score * 100.0).roundToInt() / 100.0)
                if (!it.exists()) {
                    reference.child(user.displayName.toString()).setValue(finalScore)
                } else if (score > it.value as Double) {
                    reference.child(user.displayName.toString()).setValue(finalScore)
                }
            }
        }
    }

    private fun updateScore(score: Float) {
        this.score = score
        runOnUiThread {
            binding.scoreTextView.text =
                resources.getString(R.string.score_place_holder_txt).format(score)
        }
    }

    private fun quitGame() {
        updateScore(0F)
        addGameFinishDialog.cancel()
        startActivity(
            Intent(
                this@GameActivity,
                MenuActivity::class.java
            )
        )
        finish()
    }

    private fun setUpSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val sides = -event.values[0]
            val upDown = event.values[1]

            if (sides < -movementSensitivity) {
                Snake.alive = true
                if (Snake.direction != Directions.RIGHT)
                    Snake.direction = Directions.LEFT
            }
            if (sides > movementSensitivity) {
                Snake.alive = true
                if (Snake.direction != Directions.LEFT)
                    Snake.direction = Directions.RIGHT
            }
            if (upDown < -movementSensitivity) {
                Snake.alive = true
                if (Snake.direction != Directions.DOWN)
                    Snake.direction = Directions.UP
            }
            if (upDown > movementSensitivity) {
                Snake.alive = true
                if (Snake.direction != Directions.UP)
                    Snake.direction = Directions.DOWN
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    override fun onStop() {
        super.onStop()
        Snake.reset()
        Snake.alive = false
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}