package com.abaga129.tekisuto.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.ProfileEntity
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileManagerActivity : BaseEdgeToEdgeActivity() {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var noProfilesText: TextView
    private lateinit var addProfileButton: Button
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_manager)
        
        // Apply insets to the root view to avoid status bar overlap
        applyInsetsToView(android.R.id.content)
        
        // Setup action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.profile_manager_title)
        }
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
        
        // Initialize UI components
        recyclerView = findViewById(R.id.profile_recycler_view)
        noProfilesText = findViewById(R.id.no_profiles_text)
        addProfileButton = findViewById(R.id.add_profile_button)
        
        // Setup RecyclerView
        adapter = ProfileAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Observe profiles
        viewModel.profiles.observe(this) { profiles ->
            if (profiles.isEmpty()) {
                noProfilesText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                noProfilesText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.submitList(profiles)
            }
        }
        
        // Setup add profile button
        addProfileButton.setOnClickListener {
            showProfileDialog()
        }
        
        // Load profiles
        viewModel.loadProfiles()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showProfileDialog(profile: ProfileEntity? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_edit, null)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        val profileNameInput = dialogView.findViewById<EditText>(R.id.profile_name_input)
        val defaultCheckBox = dialogView.findViewById<CheckBox>(R.id.default_profile_checkbox)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        
        // Set dialog title based on whether it's an edit or create operation
        dialogTitle.setText(if (profile == null) R.string.add_profile else R.string.edit_profile)
        
        // Populate fields if editing
        if (profile != null) {
            profileNameInput.setText(profile.name)
            defaultCheckBox.isChecked = profile.isDefault
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            val name = profileNameInput.text.toString().trim()
            if (name.isEmpty()) {
                profileNameInput.error = getString(R.string.profile_name_hint)
                return@setOnClickListener
            }
            
            if (profile == null) {
                // Create new profile
                val newProfile = ProfileEntity(
                    name = name,
                    isDefault = defaultCheckBox.isChecked
                )
                viewModel.createProfile(newProfile)
                Toast.makeText(this, R.string.profile_created, Toast.LENGTH_SHORT).show()
            } else {
                // Update existing profile
                val updatedProfile = profile.copy(
                    name = name,
                    isDefault = defaultCheckBox.isChecked
                )
                viewModel.updateProfile(updatedProfile)
                Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show()
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun confirmDeleteProfile(profile: ProfileEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage(R.string.delete_profile_confirm)
            .setPositiveButton(R.string.delete_profile) { _, _ ->
                viewModel.deleteProfile(profile)
                Toast.makeText(this, R.string.profile_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showProfileOptions(view: View, profile: ProfileEntity) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
        
        // Hide "Set as Default" option if already default
        if (profile.isDefault) {
            popup.menu.findItem(R.id.action_set_default).isVisible = false
        }
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    showProfileDialog(profile)
                    true
                }
                R.id.action_delete -> {
                    confirmDeleteProfile(profile)
                    true
                }
                R.id.action_set_default -> {
                    viewModel.setAsDefault(profile)
                    Toast.makeText(this, getString(R.string.profile_switched, profile.name), Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    inner class ProfileAdapter : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {
        
        private var profiles = emptyList<ProfileEntity>()
        
        fun submitList(newProfiles: List<ProfileEntity>) {
            profiles = newProfiles
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile, parent, false)
            return ProfileViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            holder.bind(profiles[position])
        }
        
        override fun getItemCount(): Int = profiles.size
        
        inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameText: TextView = itemView.findViewById(R.id.profile_name_text)
            private val dateText: TextView = itemView.findViewById(R.id.profile_date_text)
            private val defaultChip: Chip = itemView.findViewById(R.id.profile_default_chip)
            private val optionsButton: ImageButton = itemView.findViewById(R.id.profile_options_button)
            
            private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            
            fun bind(profile: ProfileEntity) {
                nameText.text = profile.name
                dateText.text = dateFormat.format(profile.createdDate)
                
                defaultChip.visibility = if (profile.isDefault) View.VISIBLE else View.GONE
                
                itemView.setOnClickListener {
                    viewModel.setAsDefault(profile)
                    Toast.makeText(itemView.context, 
                        itemView.context.getString(R.string.profile_switched, profile.name), 
                        Toast.LENGTH_SHORT).show()
                }
                
                optionsButton.setOnClickListener {
                    showProfileOptions(it, profile)
                }
            }
        }
    }
}