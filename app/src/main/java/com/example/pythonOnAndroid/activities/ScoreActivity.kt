package com.example.pythonOnAndroid.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pythonOnAndroid.databinding.ActivityScoreBinding
import com.google.firebase.database.FirebaseDatabase
import java.util.SortedMap

class ScoreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScoreBinding
    private val url = "https://python-on-android-default-rtdb.europe-west1.firebasedatabase.app/"

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityScoreBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        FirebaseDatabase.getInstance(url).getReference("leaderboard").get().addOnSuccessListener {
            val map = sortedMapOf<String, Long>()
            it.children.forEach { data ->
                map[data.key.toString()] = data.value as Long
                createLeaderboard(map)
            }
        }
    }

    private fun createLeaderboard(map: SortedMap<String, Long>) {
        // todo add to linear layout
    }


}