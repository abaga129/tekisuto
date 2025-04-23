package com.abaga129.tekisuto.ui.settings

import android.os.Bundle
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity

class SettingsActivity : BaseEdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Apply insets to the root view
        applyInsetsToView(android.R.id.content)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ocr_settings_title)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}