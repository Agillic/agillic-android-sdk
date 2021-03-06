package com.agillic.kotlibapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.navigation.fragment.findNavController
import com.agillic.app.sdk.ScreenView
import kotlinx.android.synthetic.main.fragment_first.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    var mainActivity : MainActivity? = null
    var UUID = "11111111-583e-42e9-aab7-1ebe018c874e" // Some unique id for the fragment
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = (activity as MainActivity)
        view.findViewById<EditText>(R.id.loginField).setText(mainActivity?.userId)
        view.findViewById<EditText>(R.id.loginField).addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s : Editable) {
                mainActivity?.userId = s.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

        view.findViewById<Button>(R.id.button_login)?.setOnClickListener {
            var name = solution_spinner.getSelectedItem() as String
            mainActivity?.initAgillicSDK(name)
        }
        view.findViewById<Button>(R.id.button_next)?.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        val spinner: Spinner = view.findViewById(R.id.solution_spinner)
        // Create an ArrayAdapter using the string array and a default spinner layout
        val also = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.solutions,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
        }
        mainActivity?.tracker?.track(ScreenView().id(UUID).name("app_protocol://fragment/1"))
    }

    override fun onResume() {
        super.onResume( )
    }
}
