package com.volmit.iris.manager.command;

import com.volmit.iris.util.KList;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStructureVariants extends MortarCommand
{
	public CommandIrisStructureVariants()
	{
		super("variants", "var", "v");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Change or add variants in tile looking at");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Structures, please enable studio in Iris/settings.json");
			return true;
		}

		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have a wand");
			return true;
		}

		Player p = sender.player();

		try
		{
			Iris.struct.get(p).openVariants();
		}

		catch(Throwable e)
		{
			sender.sendMessage("You do not have an open structure");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}