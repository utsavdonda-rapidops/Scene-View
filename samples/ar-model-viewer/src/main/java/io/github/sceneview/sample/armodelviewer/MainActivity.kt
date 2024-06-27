package io.github.sceneview.sample.armodelviewer

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
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
import io.github.sceneview.material.setBaseColorMap
import io.github.sceneview.material.setClearCoatFactor
import io.github.sceneview.material.setMetallicRoughnessIndex
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.sample.armodelviewer.databinding.RgbLayoutDialogBinding
import io.github.sceneview.sample.doOnApplyWindowInsets
import io.github.sceneview.sample.setFullScreen
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView
    lateinit var resetButton: Button
    lateinit var colorSpinner: Spinner
    lateinit var selectColorButton: Button
    var currentIndex: Int = -1
    private lateinit var adapter: ArrayAdapter<String>
    val colorMap: MutableList<MaterialInstance> = mutableListOf()
    var glbMaterial = mutableListOf<String>("Select Material")
    var isReset = true
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

    private val rgbLayoutDialogBinding: RgbLayoutDialogBinding by lazy {
        RgbLayoutDialogBinding.inflate(layoutInflater)
    }

    private fun setOnSeekbar(
        type: String,
        typeTxt: TextView,
        seekBar: SeekBar,
        colorTxt: TextView
    ) {
        typeTxt.text = type
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                colorTxt.text = seekBar.progress.toString()
                setRGBColor()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}

        })
        colorTxt.text = seekBar.progress.toString()
    }

    private fun setRGBColor(): String {
        val red = rgbLayoutDialogBinding.redLayout.seekBar.progress
        val green = rgbLayoutDialogBinding.greenLayout.seekBar.progress
        val blue = rgbLayoutDialogBinding.blueLayout.seekBar.progress
        val hex = String.format(
            "#%02x%02x%02x",
            red,
            green,
            blue
        )
        rgbLayoutDialogBinding.colorView.setBackgroundColor(Color.parseColor(hex))
        return hex
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("abcd test", "onCreate: called with savedInstanceState=$savedInstanceState")
        val constraint = findViewById<ConstraintLayout>(R.id.rootView)
        resetButton = findViewById(R.id.button4)
        colorSpinner = findViewById(R.id.colorSpinner)
        selectColorButton = findViewById(R.id.b1)

        val rgbDialog = Dialog(this).apply {
            setContentView(rgbLayoutDialogBinding.root)
            window!!.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCancelable(false)
        }

        setOnSeekbar(
            "R",
            rgbLayoutDialogBinding.redLayout.typeTxt,
            rgbLayoutDialogBinding.redLayout.seekBar,
            rgbLayoutDialogBinding.redLayout.colorValueTxt
        )
        setOnSeekbar(
            "G",
            rgbLayoutDialogBinding.greenLayout.typeTxt,
            rgbLayoutDialogBinding.greenLayout.seekBar,
            rgbLayoutDialogBinding.greenLayout.colorValueTxt
        )
        setOnSeekbar(
            "B",
            rgbLayoutDialogBinding.blueLayout.typeTxt,
            rgbLayoutDialogBinding.blueLayout.seekBar,
            rgbLayoutDialogBinding.blueLayout.colorValueTxt
        )
        rgbLayoutDialogBinding.cancelBtn.setOnClickListener {
            rgbDialog.dismiss()
        }
        rgbLayoutDialogBinding.pickBtn.setOnClickListener {
            val red = rgbLayoutDialogBinding.redLayout.seekBar.progress + 0.0f
            val green = rgbLayoutDialogBinding.greenLayout.seekBar.progress + 0.0f
            val blue = rgbLayoutDialogBinding.blueLayout.seekBar.progress + 0.0f
            if (currentIndex in colorMap.indices) {
                colorMap[currentIndex] = colorMap[currentIndex].apply {
                    setParameter("baseColorFactor", red, green, blue, 1.0f)
                }
                isReset = false
            } else {
                Log.e("LogDB", "Invalid currentIndex: $currentIndex")
            }
            rgbDialog.dismiss()
        }


        selectColorButton.setOnClickListener {
            rgbDialog.show()
        }

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
//                if (selectedMaterial != "Select Material") {
//                    val materialIndex = selectIndex.indexOf(selectedMaterial) - 1
//                    if (materialIndex >= 0 && defaultMaterialInstances != null) {
//                        val materialInstance = defaultMaterialInstances!![materialIndex]
//                        materialInstance.setParameter("baseColorFactor", x, y, z, w)
//                        reloadModel(anchorNode!!)
//                    }
//                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        resetButton.setOnClickListener {
            glbMaterial.clear()
            colorMap.clear()
// anchorNode?.position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
            resetButton.visibility = View.INVISIBLE
            selectColorButton.visibility = View.INVISIBLE
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
                            selectColorButton.visibility = View.VISIBLE
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
                    buildModelNode()?.let {modelNode ->
                        // Create a white texture
                        val whiteTexture = createWhiteTexture()

                        modelNode.modelInstance.materialInstances.forEach { materialInstance ->
                            materialInstance.setBaseColorMap(whiteTexture)
                        }
                        Log.d("abcd test", "addAnchorNode: model node built successfully")
                        addChildNode(modelNode)
                    }
                    resetButton.visibility = View.VISIBLE
                    selectColorButton.visibility = View.VISIBLE
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
                glbMaterial.add(materialInstance.name)
                Log.d("error 5", "========================================================")
                materialInstance.setMetallicRoughnessIndex(0)
                materialInstance.setClearCoatFactor(0.0f)
                if (index < colorMap.size) {
                    colorMap[index] = materialInstance
                } else {
                    colorMap.add(materialInstance)
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

    private fun createWhiteTexture(): Texture {
        val width = 1
        val height = 1
        val buffer = ByteBuffer.allocateDirect(4)
        buffer.put(0, 0x00.toByte())  // Red
        buffer.put(1, 0x00.toByte())  // Green
        buffer.put(2, 0x00.toByte())  // Blue
        buffer.put(3, 0x00.toByte())  // Alpha

        Log.d("error 1", "========================================================")

        val texture = Texture.Builder()
            .width(width)
            .height(height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_3D)
            .format(Texture.InternalFormat.RGBA8)
            .build(sceneView.engine)

        Log.d("error 2", "========================================================")

        val pixelBufferDescriptor = Texture.PixelBufferDescriptor(
            buffer,
            Texture.Format.RGBA,
            Texture.Type.UBYTE
        )

        Log.d("error 3", "========================================================")

        texture.setImage(sceneView.engine, 0, pixelBufferDescriptor)

        Log.d("error 4", "========================================================")

        return texture
    }

}