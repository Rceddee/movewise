package com.example.movewise.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.movewise.R
import com.example.movewise.model.ChatRepository

class ChatPersonalizationFragment(private val repository: ChatRepository) : Fragment(), com.example.movewise.model.DataRepository.DataListener {
    private val repo by lazy { com.example.movewise.model.DataRepository.getInstance() }
    private lateinit var etName: EditText
    private lateinit var spinnerTone: Spinner
    private lateinit var spinnerFocus: Spinner
    private lateinit var toneAdapter: ArrayAdapter<CharSequence>
    private lateinit var focusAdapter: ArrayAdapter<CharSequence>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chatbot_personalization, container, false)

        etName = view.findViewById(R.id.et_persona_name)
        spinnerTone = view.findViewById(R.id.spinner_tone)
        spinnerFocus = view.findViewById(R.id.spinner_focus)
        val btnSave: Button = view.findViewById(R.id.btn_save_persona)

        // Set up spinners
        toneAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.tones, android.R.layout.simple_spinner_item)
        toneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTone.adapter = toneAdapter

        focusAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.focus_areas, android.R.layout.simple_spinner_item)
        focusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFocus.adapter = focusAdapter

        loadPersonaData()

        btnSave.setOnClickListener {
            repository.updatePersona(
                etName.text.toString(),
                spinnerTone.selectedItem.toString(),
                spinnerFocus.selectedItem.toString()
            )
            Toast.makeText(context, "Persona Updated!", Toast.LENGTH_SHORT).show()
        }

        repo.addListener(this)
        return view
    }

    private fun loadPersonaData() {
        val currentPersona = repository.getPersona()
        etName.setText(currentPersona.name)
        spinnerTone.setSelection(toneAdapter.getPosition(currentPersona.tone))
        spinnerFocus.setSelection(focusAdapter.getPosition(currentPersona.focus))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.removeListener(this)
    }

    override fun onDataChanged() {
        if (isAdded) {
            activity?.runOnUiThread {
                loadPersonaData()
            }
        }
    }
}
