package com.abaga129.tekisuto.ui.dictionary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.abaga129.tekisuto.R

class DictionaryBrowserActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_browser)
        
        // Set up back button in action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.dictionary_browser_title)
        }
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.dictionary_container, DictionaryBrowserFragment())
                .commit()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}