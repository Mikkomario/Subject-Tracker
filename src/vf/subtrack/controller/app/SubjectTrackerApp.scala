package vf.subtrack.controller.app

import utopia.firmament.context.BaseContext
import utopia.firmament.localization.{Localizer, NoLocalization}
import utopia.firmament.model.Margins
import utopia.flow.async.context.ThreadPool
import utopia.flow.time.TimeExtensions._
import utopia.flow.util.logging.{Logger, SysErrLogger}
import utopia.flow.view.mutable.eventful.EventfulPointer
import utopia.genesis.handling.mutable.ActorHandler
import utopia.genesis.handling.{ActorLoop, KeyStateListener}
import utopia.genesis.text.Font
import utopia.genesis.util.Screen
import utopia.genesis.view.GlobalKeyboardEventHandler
import utopia.paradigm.generic.ParadigmDataType
import utopia.paradigm.measurement.DistanceExtensions._
import utopia.paradigm.shape.shape2d.insets.Insets
import utopia.paradigm.transform.Adjustment
import utopia.reach.container.RevalidationStyle
import utopia.reach.context.{ReachContentWindowContext, ReachWindowContext}
import utopia.reach.cursor.DragTo
import utopia.reach.window.ReachWindow
import vf.subtrack.util.Common
import vf.subtrack.view.component.SubjectCountMeter

import java.awt.event.KeyEvent
import scala.concurrent.ExecutionContext

/**
 * The main App class for the Subject Tracker project
 *
 * @author Mikko Hilpinen
 * @since 19/01/2024, v0.1
 */
object SubjectTrackerApp extends App
{
	// SETUP ------------------------
	
	ParadigmDataType.setup()
	
	import Screen.ppi
	
	private implicit val log: Logger = SysErrLogger
	private implicit val exc: ExecutionContext = new ThreadPool("Subject-Tracker")
	private implicit val localizer: Localizer = NoLocalization
	private implicit val languageCode: String = "en"
	
	import Common._
	
	private val actorHandler = ActorHandler()
	private val actorLoop = new ActorLoop(actorHandler)
	
	private val uiScaling = 1.0
	private val margins = Margins.implicitly((0.5.cm.toPixels * uiScaling).round.toInt)(Adjustment(0.5))
	private val baseContext = BaseContext(actorHandler, Font("Arial", (0.8.cm.toPixels * uiScaling).round.toInt),
		color.scheme, margins)
	private implicit val windowContext: ReachContentWindowContext =
		ReachWindowContext(actorHandler, color.primary.light)
			.resizable.withRevalidationStyle(RevalidationStyle.Delayed.by(0.1.seconds, 0.6.seconds))
			.withContentContext(baseContext)
	
	
	// DATA ------------------------
	
	private val openSubjectCountPointer = EventfulPointer(0)
	private val coveredSubjectCountPointer = EventfulPointer(0)
	
	
	// DEPENDENCIES ---------------
	
	coveredSubjectCountPointer.addContinuousListener { c => openSubjectCountPointer.update { _ max c.newValue } }
	
	
	// VIEW -----------------------
	
	private val window = ReachWindow.contentContextual.borderless
		.withWindowBackground(color.gray.dark)
		.using(SubjectCountMeter, title = "Subject-Tracker") { (_, meterF) =>
			meterF(openSubjectCountPointer, coveredSubjectCountPointer, 7)
		}
	window.setToCloseOnEsc()
	window.setToExitOnClose()
	
	DragTo.resize.repositioningWindow.expandingAtSides.applyTo(window.content, Insets.symmetric(margins.medium))
	
	
	// USER-INTERACTION -----------
	
	GlobalKeyboardEventHandler += KeyStateListener.onAnyKeyPressed { event =>
		if (event.keyStatus(KeyEvent.VK_CONTROL)) {
			if (event.index == KeyEvent.VK_RIGHT)
				openSubjectCountPointer.update { c => (c + 1) min 7 }
			else if (event.index == KeyEvent.VK_UP)
				coveredSubjectCountPointer.update { c => (c + 1) min 7 }
			else if (event.index == KeyEvent.VK_0) {
				coveredSubjectCountPointer.value = 0
				openSubjectCountPointer.value = 0
			}
		}
	}
	
	
	// APP CODE -------------------
	
	actorLoop.runAsync()
	window.display()
}
