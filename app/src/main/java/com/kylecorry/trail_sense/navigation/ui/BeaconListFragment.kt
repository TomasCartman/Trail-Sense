package com.kylecorry.trail_sense.navigation.ui

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentBeaconListBinding
import com.kylecorry.trail_sense.navigation.domain.BeaconGroupEntity
import com.kylecorry.trail_sense.navigation.infrastructure.persistence.BeaconRepo
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trailsensecore.domain.navigation.Beacon
import com.kylecorry.trailsensecore.domain.navigation.BeaconGroup
import com.kylecorry.trailsensecore.domain.navigation.IBeacon
import com.kylecorry.trailsensecore.infrastructure.view.ListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class BeaconListFragment : Fragment() {

    private val beaconRepo by lazy { BeaconRepo.getInstance(requireContext()) }
    private val gps by lazy { sensorService.getGPS() }

    private var _binding: FragmentBeaconListBinding? = null
    private val binding get() = _binding!!
    private lateinit var beaconList: ListView<IBeacon>
    private lateinit var navController: NavController
    private val sensorService by lazy { SensorService(requireContext()) }
    private var displayedGroup: BeaconGroup? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBeaconListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val beaconRecyclerView = binding.beaconRecycler
        beaconList =
            ListView(beaconRecyclerView, R.layout.list_item_beacon, this::updateBeaconListItem)
        beaconList.addLineSeparator()
        navController = findNavController()

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                updateBeaconList()
            }
        }

        binding.createBeaconBtn.setOnClickListener {
            if (displayedGroup != null) {
                val bundle = bundleOf("initial_group" to displayedGroup!!.id)
                navController.navigate(
                    R.id.action_beaconListFragment_to_placeBeaconFragment,
                    bundle
                )
            } else {
                val builder = AlertDialog.Builder(requireContext())
                builder.apply {
                    setTitle(getString(R.string.beacon_create))
                    setPositiveButton(getString(R.string.beacon_create_beacon)) { dialog, _ ->
                        navController.navigate(R.id.action_beaconListFragment_to_placeBeaconFragment)
                        dialog.dismiss()
                    }
                    setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    setNegativeButton(getString(R.string.beacon_create_group)) { dialog, _ ->
                        editTextDialog(
                            requireContext(),
                            getString(R.string.beacon_create_group),
                            getString(R.string.beacon_group_name_hint),
                            null,
                            null,
                            getString(R.string.dialog_ok),
                            getString(R.string.dialog_cancel)
                        ) { cancelled, text ->
                            if (!cancelled) {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO){
                                        beaconRepo.addBeaconGroup(BeaconGroupEntity(text ?: ""))
                                    }

                                    withContext(Dispatchers.Main){
                                        updateBeaconList()
                                    }
                                }
                            }
                        }
                        dialog.dismiss()
                    }
                }

                val dialog = builder.create()
                dialog.show()
            }
        }

        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && displayedGroup != null) {
                displayedGroup = null
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        updateBeaconList()
                    }
                }
                true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        if (gps.hasValidReading) {
            onLocationUpdate()
        } else {
            gps.start(this::onLocationUpdate)
        }
    }

    override fun onPause() {
        gps.stop(this::onLocationUpdate)
        super.onPause()
    }

    private fun onLocationUpdate(): Boolean {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                updateBeaconList()
            }
        }
        return false
    }

    private fun updateBeaconEmptyText(hasBeacons: Boolean) {
        if (!hasBeacons) {
            binding.beaconEmptyText.visibility = View.VISIBLE
        } else {
            binding.beaconEmptyText.visibility = View.GONE
        }
    }

    private fun updateBeaconListItem(itemView: View, beacon: IBeacon) {
        if (beacon is Beacon) {
            val listItem = BeaconListItem(itemView, lifecycleScope, beacon, gps.location)
            listItem.onView = {
                val bundle = bundleOf("beacon_id" to beacon.id)
                navController.navigate(
                    R.id.action_beacon_list_to_beaconDetailsFragment,
                    bundle
                )
            }

            listItem.onEdit = {
                val bundle = bundleOf("edit_beacon" to beacon.id)
                navController.navigate(
                    R.id.action_beaconListFragment_to_placeBeaconFragment,
                    bundle
                )
            }

            listItem.onNavigate = {
                val bundle = bundleOf("destination" to beacon.id)
                navController.navigate(R.id.action_beacon_list_to_action_navigation, bundle)
            }

            listItem.onDeleted = {
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        updateBeaconList()
                    }
                }
            }
        } else if (beacon is BeaconGroup) {
            val listItem = BeaconGroupListItem(itemView, lifecycleScope, beacon)
            listItem.onDeleted = {
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        updateBeaconList()
                    }
                }
            }
            listItem.onEdit = {
                editTextDialog(
                    requireContext(),
                    getString(R.string.beacon_create_group),
                    getString(R.string.beacon_group_name_hint),
                    null,
                    beacon.name,
                    getString(R.string.dialog_ok),
                    getString(R.string.dialog_cancel)
                ) { cancelled, text ->
                    if (!cancelled) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                beaconRepo.addBeaconGroup(BeaconGroupEntity.from(beacon.copy(name = text ?: "")))
                            }
                            withContext(Dispatchers.Main){
                                updateBeaconList()
                            }
                        }
                    }
                }
            }
            listItem.onOpen = {
                displayedGroup = beacon
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        updateBeaconList()
                    }
                }
            }
        }
    }

    private fun editTextDialog(
        context: Context,
        title: String,
        hint: String?,
        description: String?,
        initialInputText: String?,
        okButton: String,
        cancelButton: String,
        onClose: (cancelled: Boolean, text: String?) -> Unit
    ): AlertDialog {
        val layout = FrameLayout(context)
        val editTextView = EditText(context)
        editTextView.setText(initialInputText)
        editTextView.hint = hint
        layout.setPadding(64, 0, 64, 0)
        layout.addView(editTextView)

        val builder = AlertDialog.Builder(context)
        builder.apply {
            setTitle(title)
            if (description != null) {
                setMessage(description)
            }
            setView(layout)
            setPositiveButton(okButton) { dialog, _ ->
                onClose(false, editTextView.text.toString())
                dialog.dismiss()
            }
            setNegativeButton(cancelButton) { dialog, _ ->
                onClose(true, null)
                dialog.dismiss()
            }
        }

        val dialog = builder.create()
        dialog.show()
        return dialog
    }

    private suspend fun updateBeaconList() {
        context ?: return

        val beacons = withContext(Dispatchers.IO) {
            if (displayedGroup == null) {
                val ungrouped = beaconRepo.getBeaconsInGroup(null).sortedBy {
                    it.coordinate.distanceTo(gps.location)
                }.map { it.toBeacon() }

                println(ungrouped)

                val groups = beaconRepo.getGroupsSync().sortedBy {
                    it.name
                }.map { it.toBeaconGroup() }

                val all = (ungrouped + groups).map {
                    if (it is Beacon) {
                        Pair(it, it.coordinate.distanceTo(gps.location))
                    } else {
                        val groupBeacons = beaconRepo.getBeaconsInGroup(it.id).map { b ->
                            b.coordinate.distanceTo(gps.location)
                        }.minOrNull()
                        Pair(it, groupBeacons ?: Float.POSITIVE_INFINITY)
                    }
                }

                all.sortedBy { it.second }.map { it.first }
            } else {
                beaconRepo.getBeaconsInGroup(displayedGroup?.id).sortedBy {
                    it.coordinate.distanceTo(gps.location)
                }.map { it.toBeacon() }
            }
        }

        withContext(Dispatchers.Main) {
            binding.beaconTitle.text = displayedGroup?.name ?: getString(R.string.beacon_list_title)
            updateBeaconEmptyText(beacons.isNotEmpty())
            beaconList.setData(beacons)
        }
    }
}

