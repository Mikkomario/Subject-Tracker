package vf.subtrack.view.component

import utopia.firmament.context.ColorContext
import utopia.firmament.drawing.template.DrawLevel.Background
import utopia.firmament.drawing.template.{CustomDrawer, DrawLevel}
import utopia.firmament.drawing.view.BorderViewDrawer
import utopia.firmament.model.Border
import utopia.firmament.model.enumeration.SizeCategory.{VeryLarge, VerySmall}
import utopia.firmament.model.stack.StackLength
import utopia.flow.collection.CollectionExtensions._
import utopia.flow.collection.immutable.Pair
import utopia.flow.time.TimeExtensions._
import utopia.flow.util.NotEmpty
import utopia.flow.view.immutable.View
import utopia.flow.view.mutable.eventful.EventfulPointer
import utopia.flow.view.template.eventful.Changing
import utopia.genesis.graphics.{DrawSettings, Drawer}
import utopia.genesis.handling.Actor
import utopia.inception.handling.HandlerType
import utopia.paradigm.color.ColorRole.Warning
import utopia.paradigm.color.{Color, ColorRole}
import utopia.paradigm.motion.motion1d.LinearVelocity
import utopia.paradigm.path.ProjectilePath
import utopia.paradigm.shape.shape2d.area.polygon.c4.bounds.Bounds
import utopia.reach.component.factory.FromContextComponentFactoryFactory.Ccff
import utopia.reach.component.factory.contextual.ColorContextualFactory
import utopia.reach.component.hierarchy.ComponentHierarchy
import utopia.reach.component.label.empty.EmptyLabel
import utopia.reach.component.template.{PartOfComponentHierarchy, ReachComponentWrapper}
import utopia.reach.container.multi.Stack
import utopia.reach.drawing.Priority.Low
import vf.subtrack.view.component.SubjectCountMeter.{defaultAlpha, flashThreshold, maxAlphaIncrease, p}

import scala.concurrent.duration.FiniteDuration

case class MeterFactory(parentHierarchy: ComponentHierarchy, context: ColorContext, minSubjectCount: Int = 3)
	extends ColorContextualFactory[MeterFactory] with PartOfComponentHierarchy
{
	// IMPLEMENTED  --------------------------
	
	override def self: MeterFactory = this
	
	override def withContext(context: ColorContext): MeterFactory = copy(context = context)
	
	
	// OTHER    ------------------------------
	
	def withMinSubjectCount(minSubjectCount: Int) = copy(minSubjectCount = minSubjectCount)
	
	/**
	 * Creates a new subject meter
	 * @param openSubjectCountPointer A pointer that contains the number of currently open subjects
	 * @param coveredSubjectCountPointer A pointer that contains the number of adequately covered (open) subjects
	 * @param maxSubjectCount The largest allowed number of open subjects
	 * @return A new meter
	 */
	def apply(openSubjectCountPointer: Changing[Int], coveredSubjectCountPointer: Changing[Int], maxSubjectCount: Int) =
		new SubjectCountMeter(parentHierarchy, context, openSubjectCountPointer, coveredSubjectCountPointer,
			minSubjectCount min maxSubjectCount, maxSubjectCount)
}

object SubjectCountMeter extends Ccff[ColorContext, MeterFactory]
{
	// ATTRIBUTES   -------------------------
	
	private val p = ProjectilePath()
	private val maxAlphaIncrease = LinearVelocity(math.Pi, 0.7.seconds)
	private val flashThreshold = 0.4
	private val defaultAlpha = 0.8
	
	
	// IMPLEMENTED  -------------------------
	
	override def withContext(hierarchy: ComponentHierarchy, context: ColorContext): MeterFactory =
		MeterFactory(hierarchy, context)
}

/**
 * A view that displays the number of open subjects as a meter
 * @author Mikko Hilpinen
 * @since 17/01/2024, v0.1
 */
class SubjectCountMeter(parentHierarchy: ComponentHierarchy, context: ColorContext,
                        openSubjectCountPointer: Changing[Int], coveredSubjectCountPointer: Changing[Int],
                        minSubjectCount: Int, maxSubjectCount: Int)
	extends ReachComponentWrapper
{
	// ATTRIBUTES  -----------------------
	
	private val labelSize = StackLength(context.margins.medium, context.margins(VeryLarge)).expanding x
		StackLength(context.margins.medium, context.margins.large)
	
	private val fullnessPointer = openSubjectCountPointer
		.mapWhile(parentHierarchy.linkPointer) { c =>
			if (c <= minSubjectCount)
				0.0
			else
				(c - minSubjectCount) / (maxSubjectCount - minSubjectCount).toDouble
		}
	
	// The alpha variance gets exponentially faster as the meter gets full
	private val alphaIncreasePointer = fullnessPointer.map { f => maxAlphaIncrease * (1 - p(1 - f)) }
	private val isFlashingPointer = fullnessPointer.map { _ > flashThreshold }
	private val alphaPointer = EventfulPointer(1.0)
	
	// This meter consists of a stack with X labels
	private val _wrapped = Stack.withContext(parentHierarchy, context)
		.row.withMargin(VerySmall)
		.build(EmptyLabel) { labelF =>
			(0 until maxSubjectCount).toVector.map { i =>
				labelF.withCustomBackgroundDrawer(MeterColorDrawer.conditional(i))(labelSize)
			}
		}
	
	
	// INITIAL CODE ----------------------
	
	parentHierarchy.linkPointer.addContinuousListener { e =>
		if (e.newValue)
			context.actorHandler += Flasher
		else
			context.actorHandler -= Flasher
	}
	
	isFlashingPointer.addContinuousListener { e =>
		if (!e.newValue)
			alphaPointer.value = defaultAlpha
	}
	
	openSubjectCountPointer.addListenerWhile(parentHierarchy.linkPointer) { e => repaintRange(e.values.max) }
	coveredSubjectCountPointer.addListenerWhile(parentHierarchy.linkPointer) { e => repaintRange(e.values.max) }
	alphaPointer.addContinuousAnyChangeListener { repaintRange() }
	
	
	// IMPLEMENTED  ----------------------
	
	override protected def wrapped = _wrapped
	
	
	// OTHER    --------------------------
	
	private def repaintRange(repaintLabelCount: Int = openSubjectCountPointer.value) =
		NotEmpty(_wrapped.children.take(repaintLabelCount)).foreach {
			_.ends.mapAndMerge { _.bounds } { (first, last) => Bounds.aroundOption(Pair(first, last)) }
				.foreach { repaintArea(_, Low) }
		}
	
	
	// NESTED   --------------------------
	
	private object Flasher extends Actor
	{
		override def allowsHandlingFrom(handlerType: HandlerType): Boolean = isFlashingPointer.value
		
		override def act(duration: FiniteDuration): Unit = alphaPointer
			.update { a => (a + alphaIncreasePointer.value.over(duration)) % math.Pi }
	}
	
	private object MeterColorDrawer
	{
		// ATTRIBUTES   ------------------
		
		// Color pointer used until minimum subject count is passed
		private val collectingColorPointer = coveredSubjectCountPointer.map { open =>
			val shade = (open / minSubjectCount.toDouble) min 1.0
			Color.weighedAverage(Pair(context.color.primary -> (1 - shade), context.color.success -> shade))
		}
		// Color pointer used after minimum subject count has been passed
		private val fillingColorPointer = fullnessPointer.map { fullness =>
			val (themes, secondaryStrength) = {
				if (fullness < 0.33)
					Pair(ColorRole.Success, Warning) -> (fullness / 0.5)
				else if (fullness > 0.66)
					Pair(ColorRole.Failure, Warning) -> ((1 - fullness) / 0.5)
				else
					Pair(Warning, if (fullness < 0.5) ColorRole.Primary else ColorRole.Failure) ->
						((0.5 - fullness).abs / 0.5)
			}
			themes.map { context.color(_) }.merge { (p, s) =>
				Color.weighedAverage(Pair(p -> (1.0 - secondaryStrength), s -> secondaryStrength))
			}
		}
		
		private val borderDrawer = BorderViewDrawer(View { Border.symmetric(context.margins.verySmall, currentColor) })
		
		
		// COMPUTED ----------------------
		
		private def currentColor = {
			val color = {
				if (openSubjectCountPointer.value <= minSubjectCount)
					collectingColorPointer.value
				else
					fillingColorPointer.value
			}
			color.timesAlpha(0.4 + 0.6 * math.sin(alphaPointer.value))
		}
		
		
		// IMPLICIT ----------------------
		
		implicit def ds: DrawSettings = DrawSettings.onlyFill(currentColor)
		
		
		// OTHER    ---------------------
		
		def conditional(blockIndex: Int) = new ConditionalDrawer(blockIndex)
		
		
		// NESTED   ---------------------
		
		class ConditionalDrawer(blockIndex: Int) extends CustomDrawer
		{
			// IMPLEMENTED  -------------
			
			override def opaque: Boolean = openSubjectCountPointer.value > blockIndex && alphaPointer.value >= 1.0
			override def drawLevel: DrawLevel = Background
			
			override def draw(drawer: Drawer, bounds: Bounds): Unit = {
				if (coveredSubjectCountPointer.value > blockIndex)
					drawer.draw(bounds)
				else if (openSubjectCountPointer.value > blockIndex)
					borderDrawer.draw(drawer, bounds)
			}
		}
	}
}
