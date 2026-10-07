package com.foodrecommender.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodrecommender.app.R
import com.foodrecommender.app.databinding.ActivityMainBinding
import com.foodrecommender.app.presentation.viewmodels.MainUiState
import com.foodrecommender.app.presentation.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModel()
    private lateinit var binding: ActivityMainBinding
    private val adapter = PlaceAdapter()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.search(selectedRadiusMeters())
        } else {
            viewModel.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = adapter
        binding.searchButton.setOnClickListener { onSearchClicked() }
        binding.clearCacheButton.setOnClickListener { viewModel.clearSavedRestaurants() }
        binding.openSettingsButton.setOnClickListener { openAppSettings() }
        binding.radiusSpinner.adapter = android.widget.ArrayAdapter.createFromResource(
            this,
            R.array.radius_labels,
            android.R.layout.simple_spinner_dropdown_item,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun onSearchClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.search(selectedRadiusMeters())
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun selectedRadiusMeters(): Int {
        return when (binding.radiusSpinner.selectedItemPosition) {
            0 -> 500
            1 -> 1000
            2 -> 2000
            3 -> 5000
            else -> 1000
        }
    }

    private fun render(state: MainUiState) {
        val showSettings = state is MainUiState.Failed && state.showSettings
        binding.openSettingsButton.visibility = if (showSettings) View.VISIBLE else View.GONE
        when (state) {
            MainUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.idle_hint)
                adapter.submit(emptyList())
            }
            MainUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.loading)
                adapter.submit(emptyList())
            }
            is MainUiState.Ready -> {
                binding.progressBar.visibility = View.GONE
                if (state.results.isEmpty()) {
                    binding.statusText.text = getString(R.string.empty)
                    adapter.submit(emptyList())
                } else {
                    binding.statusText.text = resources.getQuantityString(
                        R.plurals.results,
                        state.results.size,
                        state.results.size,
                    )
                    adapter.submit(state.results)
                }
            }
            is MainUiState.Failed -> {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = state.message
                adapter.submit(emptyList())
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
