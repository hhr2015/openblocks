package edu.mit.custom;

import java.awt.Color;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;

import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.TextFormat;

import edu.mit.blocks.codeblocks.Block;
import edu.mit.blocks.codeblocks.BlockConnector;
import edu.mit.blocks.codeblocks.BlockConnector.PositionType;
import edu.mit.blocks.codeblockutil.Canvas;
import edu.mit.blocks.controller.WorkspaceController;
import edu.mit.blocks.codeblocks.BlockConnectorShape;
import edu.mit.blocks.codeblocks.BlockGenus;
import edu.mit.blocks.codeblocks.BlockLinkChecker;
import edu.mit.blocks.codeblocks.CommandRule;
import edu.mit.blocks.codeblocks.ParamRule;
import edu.mit.blocks.codeblocks.PolyRule;
import edu.mit.blocks.codeblocks.ProcedureOutputManager;
import edu.mit.blocks.codeblocks.SocketRule;
import edu.mit.blocks.codeblocks.StackRule;
import edu.mit.blocks.renderable.BlockImageIcon;
import edu.mit.blocks.renderable.RenderableBlock;
import edu.mit.blocks.renderable.BlockImageIcon.ImageLocation;
import edu.mit.blocks.renderable.FactoryRenderableBlock;
import edu.mit.blocks.workspace.BlockCanvas;
import edu.mit.blocks.workspace.FactoryManager;
import edu.mit.blocks.workspace.PageChangeEventManager;
import edu.mit.blocks.workspace.PageDrawerLoadingUtils;
import edu.mit.blocks.workspace.Workspace;
import edu.mit.blocks.workspace.WorkspaceEnvironment;
import edu.mit.custom.BlocksProto.BlockArg;
import edu.mit.custom.BlocksProto.BlockKind;
import edu.mit.custom.BlocksProto.Blocks;
import edu.mit.custom.BlocksProto.ConnectorArgDefault;
import edu.mit.custom.BlocksProto.ConnectorKind;
import edu.mit.custom.BlocksProto.ConnectorPosition;
import edu.mit.custom.BlocksProto.ConnectorType;
import edu.mit.custom.BlocksProto.Family;
import edu.mit.custom.SettingProto.BlockDrawer;
import edu.mit.custom.SettingProto.BlockDrawerSet;
import edu.mit.custom.SettingProto.DrawerSetLocation;
import edu.mit.custom.SettingProto.DrawerSetType;
import edu.mit.custom.SettingProto.Page;
import edu.mit.custom.SettingProto.PageDrawer;
import edu.mit.custom.SettingProto.Pages;
import edu.mit.custom.SettingProto.Setting;
import edu.mit.custom.SettingProto.TrashCan;

public class Utils {
	private static final String EMPTY_STRING = "";
	private static final String PROTO_ROOT_PATH = "resources\\protos";
	public final static String LANG_RESOURCES_PATH = "resources\\ardublock.properties";

	private static final String PREFIX_BLOCK_COLOR = "bcolor.";
	private static final String PREFIX_BLOCK_LABEL_INIT = "bg.";
	private static final String SUFFIX_BLOCK_DESCRIPTION = ".description";
	private static final String PREFIX_CONNECTOR_LABEL = "bc.";
	private static final String PREFIX_BLOCK_IMAGE = "bi.";
	private static final String PREFIX_BLOCK_DRAWER = "bd.";

	private static final String DEFAULT_BLOCK_COLOR = "128 54 54";// "bcolor."
	private static final String DEFAULT_BLOCK_KIND = "command";
	private static final String DEFAULT_BLOCK_LABELINIT = EMPTY_STRING;// "unknow";

	private static ResourceBundle bundle = null;

	static {
		if (bundle == null) {
			bundle = getLangResourceBundle();
		}
	}

	private final static String BlockGenusKind[] = { "command", "data", "function" };
	private final static String BlockGenusType[] = { "number", "number-list", "number-inv", "boolean", "boolean-list",
			"boolean-inv", "string", "string-list", "string-inv", "poly", "poly-list", "poly-inv", "proc-param",
			"cmd" };

	private final static PositionType[] PositionTypeList = { PositionType.SINGLE, PositionType.MIRROR,
			PositionType.BOTTOM };
	private final static edu.mit.blocks.renderable.BlockImageIcon.ImageLocation ImageLocationList[] = {
			ImageLocation.CENTER, ImageLocation.EAST, ImageLocation.WEST, ImageLocation.NORTH, ImageLocation.SOUTH,
			ImageLocation.SOUTHEAST, ImageLocation.SOUTHWEST, ImageLocation.NORTHEAST, ImageLocation.NORTHWEST };

	public static ResourceBundle getLangResourceBundle() {
		if (bundle == null) {
			try {
				bundle = new PropertyResourceBundle(new BufferedInputStream(new FileInputStream(LANG_RESOURCES_PATH)));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return bundle;
	}

	private static String getStringFromBundle(String temp, String msg) {
		String str = null;
		if(temp != null){
			try {
				str = bundle.getString(temp);
			} catch (Exception e) {
				System.out.println(msg);
			}
		}
		return str;
	}

	public static void loadBlockLanguage(Workspace workspace) {
		/*
		 * MUST load shapes before genuses in order to initialize connectors
		 * within each block correctly
		 */
		// BlockConnectorShape.loadBlockConnectorShapes(root);
		BlockConnectorShape.addDebugConnectionShapeMappings();

		// load genuses
		// BlockGenus.loadBlockGenera(workspace, null);
		loadBlockGenera(workspace, PROTO_ROOT_PATH);

		// load rules
		BlockLinkChecker.addRule(workspace, new CommandRule(workspace));
		BlockLinkChecker.addRule(workspace, new SocketRule());
		BlockLinkChecker.addRule(workspace, new PolyRule(workspace));
		BlockLinkChecker.addRule(workspace, new StackRule(workspace));
		BlockLinkChecker.addRule(workspace, new ParamRule());

		// set the dirty flag for the language definition file
		// to false now that the lang file has been loaded
		// langDefDirty = false;
	}

	static Map<String, List<Blocks>> GlobalBlocksProtoMap = null;

	/* TODO ***********************************************************************************************/
	private static void loadBlockGenera(Workspace workspace, String protoRootPath) {
		WorkspaceEnvironment env = workspace.getEnv();

		Map<String, List<Blocks>> blocksMap = getProtoMapFromDirectory(protoRootPath);
		GlobalBlocksProtoMap = blocksMap;
		for (String classKey : blocksMap.keySet()) {
			List<Blocks> protoList = blocksMap.get(classKey);
			// Multiple files in the same category
			for (Blocks blocks : protoList) {
				// Multiple blocks in the same proto file
				for (BlocksProto.Block block : blocks.getBlockList()) {
					BlockGenus newGenus = new BlockGenus(env);
					// set name
					newGenus.setGenusName(block.getBlockName());
					assert workspace.getEnv()
							.getGenusWithName(newGenus.getGenusName()) == null : "A block genus already exists:"
									+ newGenus.getGenusName();

					loadArg(workspace, block, newGenus);
					loadBlockArg(block, newGenus);
					loadDescription(block, newGenus);
					loadConnectors(workspace, block, newGenus);
					loadImage(block, newGenus);

					if (!newGenus.isStarter()) {
						newGenus.setBefore(new BlockConnector(workspace, BlockConnectorShape.getCommandShapeName(),
								BlockConnector.PositionType.TOP, "", false, false, Block.NULL));
					}
					if (!newGenus.isTerminator()) {
						newGenus.setAfter(new BlockConnector(workspace, BlockConnectorShape.getCommandShapeName(),
								BlockConnector.PositionType.BOTTOM, "", false, false, Block.NULL));
					}
					env.addBlockGenus(newGenus);
				}

				// load family if this file has family define (only one family
				// allow)
				for (Family family : blocks.getFamilyList()) {
					loadFamilies(workspace, family);
				}
			}
		}
	}

	private static void loadFamilies(Workspace workspace, Family family) {
		WorkspaceEnvironment env = workspace.getEnv();

		BlockGenus refGenus = env.getGenusWithName(family.getRefBlockName());
		assert refGenus != null : "Unknown Reference BlockGenus: " + family.getRefBlockName();
		assert !refGenus.isLabelEditable() : "Genus " + refGenus.getGenusName()
				+ " is in a family, but its name is editable";
		refGenus.setLabelEditable(false);

		List<String> famList = new ArrayList<String>();
		famList.add(refGenus.getGenusName());

		for (BlocksProto.Block member : family.getFamilyMemberList()) {
			assert env.getGenusWithName(member.getBlockName()) == null : "BlockGenus already exists:"
					+ member.getBlockName();
			BlockGenus newGenus = loadBlocksFromRef(workspace, refGenus, member);
			env.addBlockGenus(newGenus);
			famList.add(member.getBlockName());
		}

		if (famList.size() > 0) {
			for (String memName : famList) {
				ArrayList<String> newFamList = new ArrayList<String>(famList);
				newFamList.remove(memName);
				env.getGenusWithName(memName).setFamilyList(newFamList);
			}
		}
		famList.clear();

	}

	private static BlockGenus loadBlocksFromRef(Workspace workspace, BlockGenus refGenus,
			edu.mit.custom.BlocksProto.Block member) {
		WorkspaceEnvironment env = workspace.getEnv();
		BlockGenus newGenus = new BlockGenus(env, refGenus.getGenusName(), member.getBlockName());
		// set color
		if (member.hasBlockColor()) {
			StringTokenizer color = new StringTokenizer(member.getBlockColor());
			if (color.countTokens() == 3) {
				newGenus.setColor(new Color(Integer.parseInt(color.nextToken()), Integer.parseInt(color.nextToken()),
						Integer.parseInt(color.nextToken())));
			}
		}
		// set kind
		if (member.hasBlockKind()) {
			newGenus.setKind(BlockGenusKind[member.getBlockKind().getNumber()]);
		}
		// set init label
		String labelString = PREFIX_BLOCK_LABEL_INIT+member.getBlockName();
		if (member.hasBlockLabel()) {
			labelString = member.getBlockLabel();
		}
		if (labelString.startsWith(PREFIX_BLOCK_LABEL_INIT)) {
			String tmpString = getStringFromBundle(labelString,
					"ERROR:(" + newGenus.getGenusName() + "):can't find init label definition");
			labelString = (tmpString == null) ? labelString : tmpString;
			if (tmpString == null) {
				System.out.println("can't find:" + labelString);
			}
		}
		if (labelString != null) {
			newGenus.setInitLabel(labelString);
		}
		// set arg
		if (member.hasBlockArg()) {
			loadBlockArg(member, newGenus);
		}
		// set description
		if (member.hasBlockDescription()) {
			newGenus.getArgumentDescriptions().clear();
			loadDescription(member, newGenus);
		}
		// set connectors
		if (!member.getBlockConnectorList().isEmpty()) {
			newGenus.getSockets().clear();
			newGenus.setPlug(null);
			loadConnectors(workspace, member, newGenus);
		}
		// set image
		if (member.hasBlockImage()) {
			loadImage(member, newGenus);
		}
		return newGenus;
	}

	// blocksMap --> [key]:class [blocks]:proto files (includes block、family)
	public static Map<String, List<Blocks>> getProtoMapFromDirectory(String protoRootPath) {
		Map<String, List<Blocks>> blocksMap = new HashMap<String, List<Blocks>>();
		File root = new File(protoRootPath);
		// System.out.println("root:" + root.getAbsolutePath() + ":" +
		// root.getName());

		for (File directory : root.listFiles()) {
			// System.out.println("directory:" + directory.getAbsolutePath() +
			// ":" + directory.getName());
			if (directory.isDirectory()) {
				List<Blocks> list = new ArrayList<BlocksProto.Blocks>();
				for (File proto : directory.listFiles()) {
					//System.out.println("proto:" + proto.getAbsolutePath() + ":" + proto.getName());
					list.add(readBlocksFromFile(proto));
				}
				blocksMap.put(directory.getName(), list);
			}
		}
		// System.out.println(blocksMap.toString());
		// System.out.println(blocksMap.size());
		return blocksMap;
	}

	private static Blocks readBlocksFromFile(File proto) {
		InputStreamReader reader = null;
		Blocks.Builder builder = Blocks.newBuilder();
		try {
			reader = new InputStreamReader(new FileInputStream(proto), "utf8");
			TextFormat.merge(reader, builder);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return builder.build();
	}

	// writeBlocksToFile(blocks,"resources\\protos\\example\\example.prototxt");
	private static void writeBlocksToFile(Blocks blocks, String filePath) {
		try {
			FileOutputStream fos = new FileOutputStream(filePath);
			fos.write(blocks.toString().getBytes());
			fos.close();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void loadArg(Workspace workspace, BlocksProto.Block block, BlockGenus newGenus) {
		// set color
		String colorString = block.hasBlockColor() ? block.getBlockColor() : DEFAULT_BLOCK_COLOR;
		String temp = colorString.startsWith(PREFIX_BLOCK_COLOR) ? getStringFromBundle(colorString,
				"ERROR:(" + newGenus.getGenusName() + "):can't find color definition") : null;
		colorString = (temp == null) ? colorString : temp;
		StringTokenizer color = new StringTokenizer(colorString);
		if (color.countTokens() != 3) {
			color = new StringTokenizer(DEFAULT_BLOCK_COLOR);
			System.out.println(newGenus.getGenusName() + ":color defined error,example[255 255 255]");
		}
		newGenus.setColor(new Color(Integer.parseInt(color.nextToken()), Integer.parseInt(color.nextToken()),
				Integer.parseInt(color.nextToken())));
		// set kind
		if (block.hasBlockKind()) {
			newGenus.setKind(BlockGenusKind[block.getBlockKind().getNumber()]);
		} else {
			newGenus.setKind(DEFAULT_BLOCK_KIND);
		}
		// set init label
		String s = PREFIX_BLOCK_LABEL_INIT + newGenus.getGenusName();
		String labelString = getStringFromBundle(s,
				"ERROR:(" + newGenus.getGenusName() + "):can't find init label definition");
		newGenus.setInitLabel(labelString);
	}

	private static void loadBlockArg(BlocksProto.Block block, BlockGenus newGenus) {
		if (block.hasBlockArg()) {
			BlockArg arg = block.getBlockArg();
			newGenus.setStarter((arg.hasIsStarter() && arg.getIsStarter()) ? true : false);
			newGenus.setTerminator((arg.hasIsTerminaor() && arg.getIsTerminaor()) ? true : false);
			newGenus.setLabelValue((arg.hasLabelIsValue() && arg.getLabelIsValue()) ? true : false);
			newGenus.setLabelEditable((arg.hasLabelEditable() && arg.getLabelEditable()) ? true : false);
			newGenus.setLabelMustBeUnique((arg.hasLabelUnique() && arg.getLabelUnique()) ? true : false);
			newGenus.setPageLabelEnabled((arg.hasLabelPageEnable() && arg.getLabelPageEnable()) ? true : false);
			newGenus.setLabelPrefix(arg.hasLabelPrefix() ? arg.getLabelPrefix() : EMPTY_STRING);
			newGenus.setLabelSuffix(arg.hasLabelSuffix() ? arg.getLabelSuffix() : EMPTY_STRING);
		}
		if (newGenus.isDataBlock() || newGenus.isVariableDeclBlock() || newGenus.isFunctionBlock()) {
			newGenus.setStarter(true);
			newGenus.setTerminator(true);
		}
	}

	private static void loadDescription(BlocksProto.Block block, BlockGenus newGenus) {
		if (block.hasBlockDescription()) {

			String desc = block.getBlockDescription().hasText() ? block.getBlockDescription().getText() : " ";
			String temp = getStringFromBundle(PREFIX_BLOCK_LABEL_INIT + block.getBlockName() + SUFFIX_BLOCK_DESCRIPTION,
					"ERROR:(" + newGenus.getGenusName() + "):can't find block description definition");
			newGenus.setBlockDescription(temp == null ? desc : temp);

			for (String argDescription : block.getBlockDescription().getArgDescriptionList()) {
				newGenus.getArgumentDescriptions().add(argDescription);
			}
		}
	}

	private static void loadConnectors(Workspace workspace, BlocksProto.Block block, BlockGenus newGenus) {
		for (BlocksProto.BlockConnector conn : block.getBlockConnectorList()) {

			newGenus.setHasDefArgs(newGenus.isHasDefArgs() == false ? conn.hasConnectorDefaultArg() : true);

			String connLabel = conn.hasConnectorLabel() ? conn.getConnectorLabel() : "";
			String temp = connLabel.startsWith(PREFIX_CONNECTOR_LABEL) ? getStringFromBundle(connLabel,
					"ERROR:(" + newGenus.getGenusName() + "):can't find connector label definition") : null;
			connLabel = temp == null ? connLabel : temp;

			int position = conn.hasConnectorPosition() ? conn.getConnectorPosition().getNumber() : 0;
			boolean editable = conn.hasLabelEditable() ? conn.getLabelEditable() : false;
			boolean expandable = conn.hasIsExpandable() ? conn.getIsExpandable() : false;
			// new socket/plug
			final BlockConnector socket = new BlockConnector(workspace,
					BlockGenusType[conn.getConnectorType().getNumber()], PositionTypeList[position], connLabel,
					editable, expandable, null, Block.NULL);
			// set default arg
			if (conn.hasConnectorDefaultArg() && conn.getConnectorDefaultArg().hasDefaultArgName()) {
				ConnectorArgDefault arg = conn.getConnectorDefaultArg();
				String arglabel = null;
				if (arg.hasDefaultArgLabel()) {
					arglabel = arg.getDefaultArgLabel();
				} else {
					arglabel = getStringFromBundle(PREFIX_BLOCK_LABEL_INIT + arg.getDefaultArgName(), "ERROR:("
							+ newGenus.getGenusName() + "):can't find connector default arg label definition");
				}
				socket.setDefaultArgument(arg.getDefaultArgName(), arglabel);
			}

			if (conn.getConnectorKind() == ConnectorKind.SOCKET) {
				newGenus.getSockets().add(socket);
			} else {
				newGenus.setPlug(socket);
				assert (!socket.isExpandable()) : newGenus.getGenusName()
						+ " can not have an expandable plug.  Every block has at most one plug.";
			}

			if (socket.isExpandable()) {
				newGenus.setAreSocketsExpandable(true);
			}
		}

		if (newGenus.getSockets() != null && newGenus.getSockets().size() == 2
				&& newGenus.getSockets().get(0).getPositionType() == BlockConnector.PositionType.BOTTOM
				&& newGenus.getSockets().get(1).getPositionType() == BlockConnector.PositionType.BOTTOM) {
			newGenus.setInfix(true);
		}
	}

	private static void loadImage(BlocksProto.Block block, BlockGenus newGenus) {
		if (block.hasBlockImage()) {
			assert block.getBlockImage().hasFileLocation() : "image must be have file location:"
					+ newGenus.getGenusName();

			String temp = block.getBlockImage().getFileLocation();
			String fileLocation = temp.startsWith(PREFIX_BLOCK_IMAGE) ? getStringFromBundle(temp, "ERROR:(" + newGenus.getGenusName() + "):can't find block image definition:"+temp) : temp;

			File file = new File(fileLocation);
			assert file.exists() : "image not found(" + newGenus.getGenusName() + "):" + fileLocation;
			// System.out.println(newGenus.getGenusName()+": image
			// location:"+file.getAbsolutePath());
			file = null;

			edu.mit.blocks.renderable.BlockImageIcon.ImageLocation imagelocation = block.getBlockImage()
					.hasImageLocation() ? ImageLocationList[block.getBlockImage().getImageLocation().getNumber()]
							: ImageLocation.CENTER;
			ImageIcon icon = new ImageIcon(fileLocation);

			boolean isEditable = block.getBlockImage().hasImageEditable() ? block.getBlockImage().getImageEditable()
					: false;
			boolean textWrap = block.getBlockImage().hasImageWrapText() ? block.getBlockImage().getImageWrapText()
					: false;
			int width = block.getBlockImage().hasImageWidth() ? block.getBlockImage().getImageWidth() : -1;
			int height = block.getBlockImage().hasImageHeight() ? block.getBlockImage().getImageHeight() : -1;
			if (width > 0 && height > 0) {
				icon.setImage(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
			}
			newGenus.getBlockImageMap().put(imagelocation,
					new BlockImageIcon(icon, imagelocation, isEditable, textWrap));
		}
	}

	public static void genBlockProto() {

		BlocksProto.Blocks.Builder blocksBuilder = BlocksProto.Blocks.newBuilder();

		BlocksProto.BlockArg.Builder blockArgBuilder = BlocksProto.BlockArg.newBuilder().setIsStarter(false)
				.setIsTerminaor(false).setLabelEditable(true).setLabelPrefix("").setLabelSuffix("asd")
				.setLabelUnique(false).setLabelIsValue(false).setLabelPageEnable(false);

		BlocksProto.BlockDescription.Builder discBuilder = BlocksProto.BlockDescription.newBuilder()
				.setText("example for test").addArgDescription("first connector:number")
				.addArgDescription("second connector:string").addArgDescription("third connector:poly");

		BlocksProto.BlockConnector.Builder connBuilder1 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.NUMBER)
				.setConnectorLabel("test number").setLabelEditable(false).setIsExpandable(false)
				.setConnectorPosition(ConnectorPosition.SINGLE).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("number").setDefaultArgLabel("100"));

		BlocksProto.BlockConnector.Builder connBuilder2 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.STRING)
				.setConnectorLabel("test string").setLabelEditable(true).setIsExpandable(false)
				.setConnectorPosition(ConnectorPosition.SINGLE).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("string").setDefaultArgLabel("dfdf"));

		BlocksProto.BlockConnector.Builder connBuilder3 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.POLY)
				.setConnectorLabel("test poly").setLabelEditable(true).setIsExpandable(false)
				.setConnectorPosition(ConnectorPosition.SINGLE).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("number").setDefaultArgLabel("100"));

		BlocksProto.BlockImage.Builder imageBuilder = BlocksProto.BlockImage.newBuilder().setImageWrapText(false)
				.setImageEditable(false).setImageLocation(BlocksProto.ImageLocation.CENTER).setImageWidth(20)
				.setImageHeight(30).setFileLocation("resources/images/example.png");

		BlocksProto.Block.Builder blockbuilder = BlocksProto.Block.newBuilder().setBlockName("example")
				.setBlockKind(BlockKind.COMMAND).setBlockColor("200 0 0").setBlockLabel("init label")
				.setBlockArg(blockArgBuilder).setBlockDescription(discBuilder).addBlockConnector(connBuilder1)
				.addBlockConnector(connBuilder2).addBlockConnector(connBuilder3).setBlockImage(imageBuilder);

		BlocksProto.BlockConnector.Builder connBuilder21 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.POLY)
				.setConnectorLabel("test poly").setLabelEditable(true).setIsExpandable(false)
				.setConnectorPosition(ConnectorPosition.SINGLE).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("number").setDefaultArgLabel("100"));

		BlocksProto.BlockConnector.Builder connBuilder22 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.POLY)
				.setConnectorLabel("test poly").setLabelEditable(true).setIsExpandable(false)
				.setConnectorPosition(ConnectorPosition.SINGLE).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("number").setDefaultArgLabel("100"));

		BlocksProto.BlockConnector.Builder connBuilder31 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.STRING).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("string").setDefaultArgLabel("100"));

		BlocksProto.BlockConnector.Builder connBuilder32 = BlocksProto.BlockConnector.newBuilder()
				.setConnectorKind(ConnectorKind.SOCKET).setConnectorType(ConnectorType.STRING).setConnectorDefaultArg(
						ConnectorArgDefault.newBuilder().setDefaultArgName("string").setDefaultArgLabel("100"));

		BlocksProto.Block.Builder blockbuilder2 = BlocksProto.Block.newBuilder().setBlockName("example2")
				.setBlockLabel("init label2").addBlockConnector(connBuilder21).addBlockConnector(connBuilder22);

		BlocksProto.Block.Builder blockbuilder3 = BlocksProto.Block.newBuilder().setBlockName("example3")
				.setBlockLabel("init label3").addBlockConnector(connBuilder31).addBlockConnector(connBuilder32);

		BlocksProto.Family.Builder familybuilder = BlocksProto.Family.newBuilder().setRefBlockName("example")
				.addFamilyMember(blockbuilder2).addFamilyMember(blockbuilder3);

		Blocks blocks = blocksBuilder.addBlock(blockbuilder).addFamily(familybuilder).build();

		// System.out.println(blocks.toString());
		writeBlocksToFile(blocks, "resources\\protos\\example\\example.prototxt");
	}

	public static void genSettingProto() {
		Setting.Builder settingBuilder = Setting.newBuilder();

		TrashCan.Builder trashCanBuilder = TrashCan.newBuilder()
				.setOpenTrashCanImage("resources\\images\\trash_open.png")
				.setCloseTrashCanImage("resources\\images\\trash.png");

		Page.Builder pageBuilder = Page.newBuilder().setPageName("Main").setPageWidth(400).setPageColor("128 128 128");

		Pages.Builder pagesBuilder = Pages.newBuilder().setDrawerWithPage(true).addPage(pageBuilder);

		BlockDrawer.Builder blockDrawerBuilder1 = BlockDrawer.newBuilder().setButtonColor("29 152 155")
				.setDrawerName("bd.varible").addMemberName("number").addMemberName("number_single")
				.addMemberName("number_list").addMemberName("number_float");

		BlockDrawer.Builder blockDrawerBuilder2 = BlockDrawer.newBuilder().setButtonColor("64 109 0")
				.setDrawerName("bd.function");

		BlockDrawer.Builder blockDrawerBuilder3 = BlockDrawer.newBuilder().setButtonColor("93 13 125")
				.setDrawerName("bd.ctrl").addMemberName("netWork").addMemberName("type_float")
				.addMemberName("layer_def").addMemberName("layer_ref").addMemberName("ctrl_multi_serial")
				.addMemberName("ctrl_multi_parallel");

		BlockDrawerSet.Builder blockDrawerSetBuilder = BlockDrawerSet.newBuilder().setDrawerSetName("factory")
				.setDrawerSetType(DrawerSetType.STACK).setDrawerSetLocation(DrawerSetLocation.SOUTHWEST)
				.setWindowPerDrawer(false).setDrawerDraggable(false).addBlockDrawer(blockDrawerBuilder1)
				.addBlockDrawer(blockDrawerBuilder2).addBlockDrawer(blockDrawerBuilder3);

		Setting setting = settingBuilder.addBlockDrawerSet(blockDrawerSetBuilder).addPage(pagesBuilder)
				.setTrashCan(trashCanBuilder).build();

		writeSettingToFile(setting, PROTO_SETTING);

		Setting setting2 = readSettingFromFile(new File(PROTO_SETTING));
		System.out.println(setting2.toString());
	}

	private static Setting readSettingFromFile(File proto) {
		assert proto.exists() : "can't find : " + PROTO_SETTING;
		InputStreamReader reader = null;
		Setting.Builder builder = Setting.newBuilder();
		try {
			reader = new InputStreamReader(new FileInputStream(proto), "utf8");
			TextFormat.merge(reader, builder);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return builder.build();
	}

	// writeSettingToFile(blocks,PROTO_SETTING);
	private static void writeSettingToFile(Setting setting, String filePath) {
		try {
			FileOutputStream fos = new FileOutputStream(filePath);
			fos.write(setting.toString().getBytes());
			fos.close();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* ODO ************************************************************************************************/

	public static void loadProjectFromPath(Workspace workspace, String saveFilePath) {

		// TODO saveFilePath
		Utils.loadSetting(null, workspace);
	}

	static Setting GlobalSettingProto = null;

	private static final String PROTO_SETTING = "resources\\protos\\setting.prototxt";

	public static void loadSetting(WorkspaceController workspaceController, Workspace w) {
		Workspace workspace = (w == null) ? workspaceController.getWorkspace() : w;
		FactoryManager manager = workspace.getFactoryManager();
		Setting setting = readSettingFromFile(new File(PROTO_SETTING));
		GlobalSettingProto = setting;
		WorkspaceEnvironment env = workspace.getEnv();
		env.setNextBlockID(1);
		// reset procedure output information POM finishload
		ProcedureOutputManager.finishLoad();

		loadPageFromProto(workspace, manager, setting.getPageList().get(0));
		loadDrawerSetFromProto(workspace, manager, setting.getBlockDrawerSetList());
		if (workspaceController != null) {
			workspaceController.setWorkspaceLoaded(true);
		}
	}

	// GlobalBlocksProtoMap
	private static void loadDrawerSetFromProto(Workspace workspace, FactoryManager manager,
			List<BlockDrawerSet> drawerSetList) {

		for (BlockDrawerSet drawerSet : drawerSetList) {
			for (BlockDrawer drawer : drawerSet.getBlockDrawerList()) {
				// get name
				String drawerName = drawer.getDrawerName();
				String tempName = drawerName.startsWith(PREFIX_BLOCK_DRAWER) ? getStringFromBundle(drawerName, "ERROR:("
						+ drawer + "):can't find drawer name definition") : null;
				drawerName = (tempName == null) ? drawerName : tempName;
				// get color
				String colorString = drawer.hasButtonColor() ? drawer.getButtonColor() : DEFAULT_BLOCK_COLOR;
				String tempcolor = colorString.startsWith(PREFIX_BLOCK_COLOR) ? getStringFromBundle(colorString, "ERROR:("
						+ drawer + "):can't find drawer color definition") : null;
				colorString = (tempcolor == null) ? colorString : tempcolor;
				StringTokenizer color = new StringTokenizer(colorString);
				if (color.countTokens() != 3) {
					color = new StringTokenizer(DEFAULT_BLOCK_COLOR);
				}
				Color buttonColor = new Color(Integer.parseInt(color.nextToken()), Integer.parseInt(color.nextToken()),
						Integer.parseInt(color.nextToken()));
				// add
				manager.addStaticDrawer(drawerName, buttonColor);

				ArrayList<RenderableBlock> drawerRBs = new ArrayList<RenderableBlock>();
				for (String member : drawer.getMemberNameList()) {
					assert workspace.getEnv().getGenusWithName(member) != null : "Unknown BlockGenus: " + member;
					Block newBlock = null;
					try {
						newBlock = new Block(workspace, member, false);
					} catch (Exception e) {
						System.out.println("error while loading drawerSet from proto:" + member);
					}
					drawerRBs.add(new FactoryRenderableBlock(workspace, manager, newBlock.getBlockID()));
				}
				manager.addStaticBlocks(drawerRBs, drawerName);
			}
		}

	}

	private static void loadPageFromProto(Workspace workspace, FactoryManager manager, Pages pages) {
		// load pages, page drawers, and their blocks from save file
		// PageDrawerManager.loadPagesAndDrawers(root);
		// PageDrawerLoadingUtils.loadPagesAndDrawers(workspace, root,
		// workspace.getFactoryManager());

		List<edu.mit.blocks.workspace.Page> pageList = new ArrayList<edu.mit.blocks.workspace.Page>();
		LinkedHashMap<String, ArrayList<RenderableBlock>> blocksForDrawers = new LinkedHashMap<String, ArrayList<RenderableBlock>>();
		LinkedHashMap<edu.mit.blocks.workspace.Page, ArrayList<RenderableBlock>> blocksForPages = new LinkedHashMap<edu.mit.blocks.workspace.Page, ArrayList<RenderableBlock>>();

		Workspace.everyPageHasDrawer = pages.hasDrawerWithPage() ? pages.getDrawerWithPage() : false;

		// boolean isBlankPage = false;

		for (Page page : pages.getPageList()) {
			edu.mit.blocks.workspace.Page newPage = ConvertPageFromProto(workspace, page);

			if (Workspace.everyPageHasDrawer) {
				manager.addDynamicDrawer(page.getPageDrawer());
				ArrayList<RenderableBlock> drawerBlocks = new ArrayList<RenderableBlock>();
				List<PageDrawer> pageDrawers = page.getDrawerList();
				for (PageDrawer drawer : pageDrawers) {
					for (String drawerName : drawer.getBlockGenusMemberList()) {
						assert workspace.getEnv().getGenusWithName(drawerName) != null : "Unknown BlockGenus: "
								+ drawerName;
						Block block = new Block(workspace, drawerName);
						drawerBlocks.add(new FactoryRenderableBlock(workspace, manager, block.getBlockID()));
					}
					blocksForDrawers.put(null, drawerBlocks);
					break;
				}
			}

			// we add to the end of the set of pages
			int position = pageList.size();
			// add to workspace
			if (position == 0) {
				// replace the blank default page
				workspace.putPage(newPage, 0);
			} else {
				workspace.addPage(newPage, position);
			}
			pageList.add(position, newPage);

			blocksForPages.put(newPage, new ArrayList<RenderableBlock>());
		}

		// add blocks in drawers
		for (String d : blocksForDrawers.keySet()) {
			manager.addDynamicBlocks(blocksForDrawers.get(d), d);
		}
		// blocks in pages
		for (edu.mit.blocks.workspace.Page p : blocksForPages.keySet()) {
			p.addLoadedBlocks(blocksForPages.get(p), false);
		}

		// FIXME: this UI code should not be here, fails unit tests that run in
		// headless mode
		// As a workaround, only execute if we have a UI
		if (!GraphicsEnvironment.isHeadless()) {
			int screenWidth = java.awt.Toolkit.getDefaultToolkit().getScreenSize().width;
			BlockCanvas blockCanvas = workspace.getBlockCanvas();
			JComponent canvas = blockCanvas.getCanvas();
			int canvasWidth = canvas.getPreferredSize().width;
			if (canvasWidth < screenWidth) {
				edu.mit.blocks.workspace.Page p = blockCanvas.getPageAt(blockCanvas.numOfPages() - 1);
				p.addPixelWidth(screenWidth - canvasWidth);
				PageChangeEventManager.notifyListeners();
			}
		}
	}

	private static edu.mit.blocks.workspace.Page ConvertPageFromProto(Workspace workspace, Page page) {
		String pageName = page.getPageName();
		int pageWidth = page.hasPageWidth() ? page.getPageWidth() : 0;
		String pageDrawer = page.hasPageDrawer() ? page.getPageDrawer() : null;

		String color = page.hasPageColor() ? page.getPageColor() : DEFAULT_BLOCK_COLOR;
		String tempStr = color.startsWith(PREFIX_BLOCK_COLOR) ? getStringFromBundle(color, "ERROR:("
				+ pageName + "):can't find page color definition") : null;
		color = (tempStr == null) ? color : tempStr;

		StringTokenizer col = new StringTokenizer(color);
		if (col.countTokens() != 3) {
			col = new StringTokenizer(DEFAULT_BLOCK_COLOR);
		}
		Color pageColor = new Color(Integer.parseInt(col.nextToken()), Integer.parseInt(col.nextToken()),
				Integer.parseInt(col.nextToken()));

		edu.mit.blocks.workspace.Page newPage = new edu.mit.blocks.workspace.Page(workspace, pageName, pageWidth, 0,
				pageDrawer, true, pageColor, false);
		newPage.setPageId(null);
		return newPage;
	}

}
