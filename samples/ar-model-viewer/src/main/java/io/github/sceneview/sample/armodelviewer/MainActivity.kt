package io.github.sceneview.sample.armodelviewer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.sample.doOnApplyWindowInsets
import io.github.sceneview.sample.setFullScreen
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView
    lateinit var resetButton: Button
    lateinit var colorSpinner: Spinner
    lateinit var buttonBlue: Button
    lateinit var buttonRed: Button

    var currentIndex: Int = -1
    private lateinit var adapter: ArrayAdapter<String>

    val colorMap: MutableList<MaterialInstance> = mutableListOf()

    var glbMaterial = mutableListOf<String>()
    var isReset = true

    var isModify = false
    var defaultMaterialInstances: List<MaterialInstance>? = null
    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }
    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }
    var anchorNodeView: View? = null
    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    fun updateInstructions() {
        Log.d("LogDB", "updateInstructions: #############################")
        instructionText.text = trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.point_your_phone_down)
        } else {
            null
        }
        Log.d(
            "LogDB",
            "updateInstructions: instructionText updated to '${instructionText.text}'"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("onCreate called")
        Log.d("LogDB", "onCreate called")
        resetButton = findViewById(R.id.button4)
        colorSpinner = findViewById(R.id.colorSpinner)
        buttonBlue = findViewById(R.id.b1)
        buttonRed = findViewById(R.id.r1)

        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, glbMaterial)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorSpinner.adapter = adapter
        colorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                val selectedMaterial = parent.getItemAtPosition(position).toString()
                currentIndex = position
                Log.d("select Value", " ${selectedMaterial} ${position}")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        buttonBlue.setOnClickListener {
            Log.d("LogDB", "buttonBlue.setOnClickListener")
            if (currentIndex in colorMap.indices) {
                colorMap[currentIndex] = colorMap[currentIndex].apply {
                    setParameter("baseColorFactor", 0.0f, 1.0f, 0.0f, 1.0f)
                }
                isReset = false
                isModify = true
//                anchorNode?.let {
//                    reloadModel(it)
//                }
            } else {
                Log.e("LogDB", "Invalid currentIndex: $currentIndex")
            }
        }

        buttonRed.setOnClickListener {
            Log.d("LogDB", "buttonRed.setOnClickListener")
            if (currentIndex in colorMap.indices) {
                colorMap[currentIndex] = colorMap[currentIndex].apply {
                    setParameter("baseColorFactor", 1.0f, 0.0f, 0.0f, 1.0f)
                }
                isReset = false
                isModify = true
//                anchorNode?.let {
//                    reloadModel(it)
//                }
            } else {
                Log.e("LogDB", "Invalid currentIndex: $currentIndex")
            }
        }

        resetButton.setOnClickListener {
            Log.d("LogDB", "resetButton.setOnClickListener")
            glbMaterial.clear()
            isModify = false
            resetButton.visibility = View.INVISIBLE
            buttonBlue.visibility = View.INVISIBLE
            buttonRed.visibility = View.INVISIBLE
            colorSpinner.visibility = View.INVISIBLE
            isReset = true
            val arSession: Session? = sceneView.session
            val currentFrame = arSession?.update()
            val cameraPose = currentFrame?.camera?.pose
            val newPosition = cameraPose?.compose(Pose.makeTranslation(0f, 0f, -1f))
            newPosition?.let {
                val newAnchor = sceneView.session?.createAnchor(it)
                newAnchor?.let { anchor ->
                    anchorNode?.let { previousAnchorNode ->
                        sceneView.removeChildNode(previousAnchorNode)
                    }
                    addAnchorNode(anchor)
                }
            }
            Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
            anchorNode?.let {
                reloadModel(it)
            }
        }

        setFullScreen(
            findViewById(R.id.rootView),
            fullScreen = true,
            hideSystemBars = false,
            fitsSystemWindows = false
        )
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar)?.apply {
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
            }
            title = ""
        })
        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)
        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {
            lifecycle = this@MainActivity.lifecycle
            planeRenderer.isEnabled = true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    frame.getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { plane ->
                            addAnchorNode(plane.createAnchor(plane.centerPose))
                            resetButton.visibility = View.VISIBLE
                            buttonBlue.visibility = View.VISIBLE
                            buttonRed.visibility = View.VISIBLE
                            colorSpinner.visibility = View.VISIBLE
                        }
                }
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }
    }

    fun addAnchorNode(anchor: Anchor) {
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor).apply {
                isEditable = true
                lifecycleScope.launch {
                    isLoading = true
                    buildModelNode()?.let {
                        addChildNode(it)
                    }
                    resetButton.visibility = View.VISIBLE
                    buttonBlue.visibility = View.VISIBLE
                    buttonRed.visibility = View.VISIBLE
                    colorSpinner.visibility = View.VISIBLE
                    isLoading = false
                }
                anchorNode = this
            }
        )
    }

    suspend fun buildModelNode(): ModelNode? {
        Log.d("LogDB", "buildModelNode")
//        glbMaterial.clear()
        return sceneView.modelLoader.loadModelInstance(
            "https://firebasestorage.googleapis.com/v0/b/flutterpractice-be455.appspot.com/o/satyam%2Fsample_curtain.glb?alt=media&token=ae90d584-87c1-4bd8-bd4b-be24f552c362"
        )?.let { modelInstance ->
            Log.d("LogDB", "buildModelNode: model instance loaded successfully")

            modelInstance.materialInstances.forEachIndexed { index, materialInstance ->
                glbMaterial.add(materialInstance.name)
                if (index < colorMap.size) {
                    colorMap[index] = materialInstance
                } else {
                    colorMap.add(materialInstance)
                }
                Log.d("LogDB", "buildModelNode: isModify false $index")
//                else {
//                    Log.d("LogDB", "UNDER ELSE PART " +
//                            "buildModelNode: isModify true $index")
//                    if (index < colorMap.size) {
//                        modelInstance.materialInstances[index] = colorMap[index]
//                    } else {
//                        Log.e("LogDB", "Invalid index in colorMap: $index")
//                    }
//                    Log.d("LogDB", "buildModelNode: isModify true $index")
//                }
            }
            adapter.notifyDataSetChanged()
            return ModelNode(
                modelInstance = modelInstance,
                // Scale to fit in a 0.5 meters cube
                scaleToUnits = 0.5f,
                // Bottom origin instead of center so the model base is on floor
                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = true
            }
        }
        return null
    }

    fun reloadModel(node: AnchorNode) {
        lifecycleScope.launch {
            isLoading = true
            Log.d("LogDB", "reloadModel: started reloading model")
            val newModelNode = buildModelNode()
            if (newModelNode != null) {
                node.clearChildNodes() // Remove existing child nodes
                node.addChildNode(newModelNode)
                Log.d("LogDB", "reloadModel: model reloaded successfully")
            }
            isLoading = false
            Log.d("LogDB", "reloadModel: finished reloading model")
        }
    }

    fun AnchorNode.clearChildNodes() {
        val nodes = ArrayList(childNodes)
        nodes.forEach {
            removeChildNode(it)
        }
    }
}
