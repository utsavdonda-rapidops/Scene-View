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
    var glbMaterial = mutableListOf<String>("Select Material")
    var isReset = true
    var a = 0.0f
    var b = 0.0f
    var c = 0.0f
    var d = 0.0f
    var x = 0.0f
    var y = 0.0f
    var z = 0.0f
    var w = 0.0f
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
        Log.d("abcd test", "updateInstructions: #############################")
        instructionText.text = trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.point_your_phone_down)
        } else {
            null
        }
        Log.d(
            "abcd test",
            "updateInstructions: instructionText updated to '${instructionText.text}'"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("abcd test", "onCreate: called with savedInstanceState=$savedInstanceState")
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
// if (selectedMaterial != "Select Material") {
// val materialIndex = selectIndex.indexOf(selectedMaterial) - 1
// if (materialIndex >= 0 && defaultMaterialInstances != null) {
// val materialInstance = defaultMaterialInstances!![materialIndex]
// materialInstance.setParameter("baseColorFactor", x, y, z, w)
// reloadModel(anchorNode!!)
// }
// }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        buttonBlue.setOnClickListener {
            a = x
            b = y
            c = z
            d = w
// Update color values
            x = 0.0f
            y = 1.0f
            z = 0.0f
            w = 1.0f
            isReset = false
            Toast.makeText(this, "Change Color Red", Toast.LENGTH_SHORT).show()
// Reload model with new color
            anchorNode?.let {
                reloadModel(it)
            }
        }
        buttonRed.setOnClickListener {
            a = x
            b = y
            c = z
            d = w
// Update color values
            x = 1.0f
            y = 0.0f
            z = 0.0f
            w = 1.0f
            isReset = false
            Toast.makeText(this, "Change Color Red", Toast.LENGTH_SHORT).show()
// Reload model with new color
            anchorNode?.let {
                reloadModel(it)
            }
        }
        resetButton.setOnClickListener {
            glbMaterial.clear()
// anchorNode?.position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
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
                Log.d("abcd test", "configureSession: configuring AR session")
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
                Log.d(
                    "abcd test",
                    "onTrackingFailureChanged: tracking failure reason changed to $reason"
                )
                this@MainActivity.trackingFailureReason = reason
            }
        }
    }

    fun addAnchorNode(anchor: Anchor) {
        Log.d("abcd test", "addAnchorNode: adding anchor node with anchor=$anchor")
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor).apply {
                isEditable = true
                lifecycleScope.launch {
                    isLoading = true
                    Log.d("abcd test", "addAnchorNode: started loading model")
                    buildModelNode()?.let {
                        Log.d("abcd test", "addAnchorNode: model node built successfully")
                        addChildNode(it)
                    }
                    resetButton.visibility = View.VISIBLE
                    buttonBlue.visibility = View.VISIBLE
                    buttonRed.visibility = View.VISIBLE
                    colorSpinner.visibility = View.VISIBLE
                    isLoading = false
                    Log.d("abcd test", "addAnchorNode: finished loading model")
                }
                anchorNode = this
            }
        )
    }

    suspend fun buildModelNode(): ModelNode? {
        Log.d("abcd test", "buildModelNode: loading model instance")
        glbMaterial.clear()
        return sceneView.modelLoader.loadModelInstance(
            "https://firebasestorage.googleapis.com/v0/b/flutterpractice-be455.appspot.com/o/satyam%2Fsample_curtain.glb?alt=media&token=ae90d584-87c1-4bd8-bd4b-be24f552c362"
        )?.let { modelInstance ->
            modelInstance.materialInstances.forEachIndexed { index, materialInstance ->
// Log.d("index 1", "name: ${modelInstance.materialInstances.get(0).name} ")
// Log.d("index 2", "name: ${modelInstance.materialInstances.get(1).name} ")
                glbMaterial.add(modelInstance.materialInstances[index].name)
                when {
                    x == 0.0f && y == 0.0f && z == 0.0f && w == 0.0f || isReset -> {
                        defaultMaterialInstances = listOf(materialInstance)
                        Log.d("YourTag", "Color: ${defaultMaterialInstances}")
                    }

                    else -> {
                        defaultMaterialInstances = listOf(materialInstance)
                        modelInstance.materialInstances[currentIndex].setParameter(
                            "baseColorFactor",
                            x,
                            y,
                            z,
                            w
                        )
// if(currentIndex==index){
// modelInstance.materialInstances[currentIndex].setParameter("baseColorFactor", x, y, z, w)
// }else{
// if(a != 0.0f || b != 0.0f || c != 0.0f || d != 0.0f){
// modelInstance.materialInstances[index].setParameter("baseColorFactor", a, b, c, d)
// }
// }
// materialInstance.setParameter("baseColorFactor", x, y, z, w)
                        Log.d("YourTag", "Color: ${defaultMaterialInstances}")
                    }
                }
            }
            adapter.notifyDataSetChanged()
            Log.d(
                "abcd test",
                "buildModelNode: model instance loaded successfully, modelInstance=${modelInstance.toString()}"
            )
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
        Log.d("abcd test", "buildModelNode: failed to load model instance")
        return null
    }

    fun reloadModel(node: AnchorNode) {
        lifecycleScope.launch {
            isLoading = true
            Log.d("abcd test", "reloadModel: started reloading model")
            val newModelNode = buildModelNode()
            if (newModelNode != null) {
                node.clearChildNodes() // Remove existing child nodes
                node.addChildNode(newModelNode)
                Log.d("abcd test", "reloadModel: model reloaded successfully")
            }
            isLoading = false
            Log.d("abcd test", "reloadModel: finished reloading model")
        }
    }

    fun AnchorNode.clearChildNodes() {
        val nodes = ArrayList(childNodes)
        nodes.forEach {
            removeChildNode(it)
        }
    }
}