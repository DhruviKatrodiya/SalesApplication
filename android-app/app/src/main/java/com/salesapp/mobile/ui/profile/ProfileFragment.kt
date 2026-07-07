package com.salesapp.mobile.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.R
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.data.repo.ProfileRepository
import com.salesapp.mobile.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!
    private val profile = ProfileRepository()
    private val auth = AuthRepository()

    /** Local file path of the current avatar; null = no image. Persisted only on Save. */
    private var imagePath: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importImage(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentProfileBinding.bind(view)
        loadMe()
        b.btnSaveProfile.setOnClickListener { saveProfile() }
        b.btnChangePassword.setOnClickListener { changePassword() }
        b.btnUpload.setOnClickListener { pickImage.launch("image/*") }
        b.btnRemoveImage.setOnClickListener { imagePath = null; showAvatar() }
    }

    private fun loadMe() {
        lifecycleScope.launch {
            val me = runCatching { profile.me() }.getOrNull() ?: Session.currentUser
            b.etFullName.setText(me?.fullName)
            b.etEmail.setText(me?.email)
            b.etPhone.setText(me?.phone)
            imagePath = me?.profileImagePath
            showAvatar()
        }
    }

    /** Copy the picked image into private app storage and show it (mirrors the website upload step). */
    private fun importImage(uri: Uri) {
        lifecycleScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(requireContext().filesDir, "profile_${Session.userId}.jpg")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not read image")
                    file.absolutePath
                }
            }.getOrElse { toast("Please select a valid image."); return@launch }
            imagePath = saved
            showAvatar()
            toast("Image selected. Tap \"Save Changes\" to apply.")
        }
    }

    private fun showAvatar() {
        val path = imagePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            b.ivAvatar.setImageResource(R.drawable.ic_account_circle)
            b.ivAvatar.imageTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.text_secondary)
            b.btnRemoveImage.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
            }
            if (bmp != null) {
                b.ivAvatar.imageTintList = null
                b.ivAvatar.setImageBitmap(bmp)
                b.btnRemoveImage.visibility = View.VISIBLE
            }
        }
    }

    private fun saveProfile() {
        val name = b.etFullName.text?.toString()?.trim().orEmpty()
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim()
        if (email.isEmpty()) { toast("Email is required."); return }
        if (!phone.isNullOrEmpty() && !phone.matches(Regex("^\\d{7,15}$"))) {
            toast("Enter a valid phone number (7–15 digits)."); return
        }
        lifecycleScope.launch {
            profile.update(name, email, phone, imagePath).fold(
                onSuccess = { user -> Session.signIn(requireContext(), user); toast("Profile updated.") },
                onFailure = { toast(it.message ?: "Save failed.") },
            )
        }
    }

    private fun changePassword() {
        val current = b.etCurrent.text?.toString().orEmpty()
        val new = b.etNew.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()
        if (current.isEmpty() || new.isEmpty()) { toast("Enter current and new passwords."); return }
        if (new.length < 6) { toast("New password must be at least 6 characters."); return }
        if (new != confirm) { toast("New passwords do not match."); return }
        lifecycleScope.launch {
            auth.changePassword(Session.userId, current, new).fold(
                onSuccess = {
                    toast("Password changed.")
                    b.etCurrent.text?.clear(); b.etNew.text?.clear(); b.etConfirm.text?.clear()
                },
                onFailure = { toast(it.message ?: "Change failed.") },
            )
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
