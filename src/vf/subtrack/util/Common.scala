package vf.subtrack.util

import utopia.paradigm.color.{Color, ColorScheme, ColorSet}

/**
 * Contains commonly shared values within this project
 *
 * @author Mikko Hilpinen
 * @since 19/01/2024, v0.1
 */
object Common
{
	// COMPUTED -----------------------
	
	/**
	 * @return Access to this project's color scheme
	 */
	def color = Colors
	
	
	// NESTED   -----------------------
	
	object Colors
	{
		import utopia.paradigm.color.ColorRole._
		
		val primary = ColorSet.fromHexes("#695f95", "#a3a0c0", "#45346f").get
		val secondary = ColorSet(Color.fromHex("#ab255c").get)
		val info = ColorSet.fromHexes("#737ea5", "#c1c6d9", "#3b4984").get
		val success = ColorSet(Color.fromHex("#74993e").get)
		val warning = ColorSet.fromHexes("#b89f18", "#d8c45f", "#917611").get
		val error = ColorSet.fromHexes("#70220d", "#a55335", "#510000").get
		
		val scheme = ColorScheme.twoTone(primary, secondary) ++ Map(
			Gray -> gray,
			Info -> info,
			Success -> success,
			Warning -> warning,
			Failure -> error
		)
		
		def gray = ColorSet.defaultDarkGray
	}
}
