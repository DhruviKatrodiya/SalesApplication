package com.salesapp.mobile.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.R
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.AuthRepository
import com.salesapp.mobile.data.repo.ProfileRepository
import com.salesapp.mobile.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!
    private val profile = ProfileRepository()
    private val auth = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentProfileBinding.bind(view)
        loadMe()
        b.btnSaveProfile.setOnClickListener { saveProfile() }
        b.btnChangePassword.setOnClickListener { changePassword() }
    }

    private fun loadMe() {
        lifecycleScope.launch {
            val me = runCatching { profile.me() }.getOrNull() ?: Session.currentUser
            b.etFullName.setText(me?.fullName)
            b.etEmail.setText(me?.email)
            b.etPhone.setText(me?.phone)
        }
    }

    private fun saveProfile() {
        val name = b.etFullName.text?.toString()?.trim().orEmpty()
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty()) { toast("Email is required."); return }
        lifecycleScope.launch {
            profile.update(name, email, b.etPhone.text?.toString()?.trim()).fold(
                onSuccess = { user -> Session.signIn(requireContext(), user); toast("Profile saved.") },
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
