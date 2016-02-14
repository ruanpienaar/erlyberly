package erlyberly;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;

import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.fontawesome.Icon;
import floatyfield.FloatyFieldView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.SplitPane.Divider;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;



public class DbgView implements Initializable {

	private static final String ICON_STYLE = "-fx-font-family: FontAwesome; -fx-font-size: 1em;";
	
	final DbgController dbgController = new DbgController();
	
	private final ObservableList<TreeItem<ModFunc>> treeModules = FXCollections.observableArrayList();
	
	private final SortedList<TreeItem<ModFunc>> sortedTreeModules = new SortedList<TreeItem<ModFunc>>(treeModules);
	
	private final FilteredList<TreeItem<ModFunc>> filteredTreeModules = new FilteredList<TreeItem<ModFunc>>(sortedTreeModules);

	/**
	 * A list of all the filtered lists for functions, so a predicate can be set on them.  Binding
	 * the predicate property does not seem to work.
	 */
	private final ObservableList<FilteredList<TreeItem<ModFunc>>> functionLists = FXCollections.observableArrayList();
	
	@FXML
	private TreeView<ModFunc> modulesTree;
	@FXML
	private VBox modulesBox;
	@FXML
	private Label noTracesLabel;
	@FXML
	private SplitPane dbgSplitPane;
	@FXML
	private HBox traceLogSearchBox;
	
	private double functionsDivPosition;

    private ModFuncContextMenu modFuncContextMenu;
	
	@Override
	public void initialize(URL url, ResourceBundle r) {
        modFuncContextMenu = new ModFuncContextMenu(dbgController);
        modFuncContextMenu.rootProperty().bind(modulesTree.rootProperty());
        modulesTree
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((o, old, newItem) -> { 
                modFuncContextMenu.selectedTreeItemProperty().set(newItem);
                if(newItem != null)
                    modFuncContextMenu.selectedItemProperty().set(newItem.getValue()); 
            });
        
		sortedTreeModules.setComparator(treeItemModFuncComparator());
		
		SplitPane.setResizableWithParent(modulesBox, Boolean.FALSE);
		
		ErlyBerly.nodeAPI().connectedProperty().addListener(this::onConnected);
		
		modulesTree.setCellFactory(new ModFuncTreeCellFactory(dbgController));
		/*modulesTree.setOnKeyPressed(this::onKeyPressInModuleTree);*/
        modulesTree.setContextMenu(modFuncContextMenu);
		
		
		addModulesFloatySearchControl();
		
		dbgController.initialize(url, r);
		
		dbgSplitPane.getItems().add(new DbgTraceView(dbgController));
	}

	private FxmlLoadable addModulesFloatySearchControl() {
		FxmlLoadable loader = new FxmlLoadable("/floatyfield/floaty-field.fxml");
		
		loader.load();

		FloatyFieldView ffView;
		
		ffView = (FloatyFieldView) loader.controller;
		ffView.promptTextProperty().set("Search functions i.e. gen_s:call or #t for all traces");
		
		loader.fxmlNode.setStyle("-fx-padding: 5 5 0 5;");
		
		HBox.setHgrow(loader.fxmlNode, Priority.ALWAYS);
		modulesBox.getChildren().add(0, loader.fxmlNode);
		
		filterTextProperty = ffView.textProperty();
        filterTextProperty.addListener(this::onFunctionSearchChange);

        TextField filterTextView;
        filterTextView = floatyFieldTextField(loader);

		Platform.runLater(() -> {
            FilterFocusManager.addFilter(filterTextView, 1);
        });
		
		return loader;
	}

    /**
     * Flag for when the shortcut for applying traces to all visisble functions
     * is pressed down, used for only executing it once per press, not once per
     * event which can be many.
     */
    static boolean toggleAllTracesDown = false;

    private StringProperty filterTextProperty;

    private TextField floatyFieldTextField(FxmlLoadable loader) {
        // FIXME floaty field should allow access to the text field
        return (TextField) loader.fxmlNode.getChildrenUnmodifiable().get(1);
    }
    
	
	public void onRefreshModules(ActionEvent e) {
		treeModules.clear();
		
		refreshModules();
	}
	
	public void onFunctionSearchChange(Observable o, String oldValue, String search) {
		if(isSpecialTraceFilter(search))
			filterForTracedFunctions();
		else
			filterForFunctionTextMatch(search);
	}

	private boolean isSpecialTraceFilter(String search) {
		return "#t".equals(search.trim());
	}

	private void filterForFunctionTextMatch(String search) {
		String[] split = search.split(":");
		
		if(split.length == 0)
			return;
		
		final String moduleName = split[0];
		final String funcName = (split.length > 1) ? split[1] : ""; 
		
		if(search.contains(":")) {
			for (TreeItem<ModFunc> treeItem : filteredTreeModules) {
				treeItem.setExpanded(true);
			}
		}
		
		for (FilteredList<TreeItem<ModFunc>> funcItemList : functionLists) {
			funcItemList.setPredicate((t) -> { return isMatchingModFunc(funcName, t); });
		}

		filteredTreeModules.setPredicate((t) -> { return isMatchingModFunc(moduleName, t) && !t.getChildren().isEmpty(); });
	}

	private void filterForTracedFunctions() {
		for (FilteredList<TreeItem<ModFunc>> funcItemList : functionLists) {
			funcItemList.setPredicate((t) -> { return dbgController.isTraced(t.getValue()); });
		}

		filteredTreeModules.setPredicate((t) -> { return !t.getChildren().isEmpty(); });
	}

	private Comparator<TreeItem<ModFunc>> treeItemModFuncComparator() {
		return new Comparator<TreeItem<ModFunc>>() {
			@Override
			public int compare(TreeItem<ModFunc> o1, TreeItem<ModFunc> o2) {
				return o1.getValue().compareTo(o2.getValue());
			}};
	}

	private boolean isMatchingModFunc(String searchText, TreeItem<ModFunc> t) {
		if(searchText.isEmpty())
			return true;
		return t.getValue().toString().contains(searchText);
	}

	private void onConnected(Observable o) {
		boolean connected = ErlyBerly.nodeAPI().connectedProperty().get();
		
		// disable buttons when not connected
		/*seqTraceMenuItem.setDisable(!connected);*/
		
		if(connected) {
			refreshModules();
			dbgController.reapplyTraces();
		}
		else {
			treeModules.clear();
		}
	}

	private void refreshModules() {
		try {
			modulesTree.setShowRoot(false);
			dbgController.requestModFuncs(this::buildObjectTreeRoot);
		} 
		catch (Exception e) {
			throw new RuntimeException("failed to build module/function tree", e);
		}
	}
	
	private void buildObjectTreeRoot(OtpErlangList requestFunctions) {
		boolean isExported;
		
		for (OtpErlangObject e : requestFunctions) {
			OtpErlangTuple tuple = (OtpErlangTuple) e;
			
			OtpErlangAtom moduleNameAtom = (OtpErlangAtom) tuple.elementAt(0);
			OtpErlangList exportedFuncs = (OtpErlangList) tuple.elementAt(1);
			OtpErlangList localFuncs = (OtpErlangList) tuple.elementAt(2);
			
			TreeItem<ModFunc> moduleItem;
			
			moduleItem = new TreeItem<ModFunc>(ModFunc.toModule(moduleNameAtom));
			moduleItem.setGraphic(treeIcon(AwesomeIcon.CUBE));
			
			ObservableList<TreeItem<ModFunc>> modFuncs = FXCollections.observableArrayList();
			
			SortedList<TreeItem<ModFunc>> sortedFuncs = new SortedList<TreeItem<ModFunc>>(modFuncs);
			
			FilteredList<TreeItem<ModFunc>> filteredFuncs = new FilteredList<TreeItem<ModFunc>>(sortedFuncs);

			sortedFuncs.setComparator(treeItemModFuncComparator());
			
			isExported = true;			
			addTreeItems(toModFuncs(moduleNameAtom, exportedFuncs, isExported), modFuncs);

			isExported = false;
			addTreeItems(toModFuncs(moduleNameAtom, localFuncs, isExported), modFuncs);
			functionLists.add(filteredFuncs);
			
			Bindings.bindContentBidirectional(moduleItem.getChildren(), filteredFuncs);
			
			treeModules.add(moduleItem);
		}

		TreeItem<ModFunc> root;
		
		root = new TreeItem<ModFunc>();
		root.setExpanded(true);
		
		Bindings.bindContentBidirectional(root.getChildren(), filteredTreeModules);

		modulesTree.setRoot(root);

        // set predicates on the function tree items so that they filter correctly
        filterForFunctionTextMatch(filterTextProperty.get());
	}
	
	private void addTreeItems(List<ModFunc> modFuncs, ObservableList<TreeItem<ModFunc>> modFuncTreeItems) {
		for (ModFunc modFunc : modFuncs) {
			if(!modFunc.isSynthetic()) {
				TreeItem<ModFunc> item = newFuncTreeItem(modFunc);
				
				modFuncTreeItems.add(item);
			}
		}
	}

	private TreeItem<ModFunc> newFuncTreeItem(ModFunc modFunc) {
		return new TreeItem<ModFunc>(modFunc);
	}


	private Icon treeIcon(AwesomeIcon treeIcon) {
		return Icon.create().icon(treeIcon).style(ICON_STYLE);
	}

	private ArrayList<ModFunc> toModFuncs(OtpErlangAtom moduleNameAtom, OtpErlangList exportedFuncs, boolean isExported) {
		ArrayList<ModFunc> mfs = new ArrayList<>();
		for (OtpErlangObject exported : exportedFuncs) {
			ModFunc modFunc = ModFunc.toFunc(moduleNameAtom, exported, isExported);
			mfs.add(modFunc);
		}
		return mfs;
	}
	
	public void setFunctionsVisibility(Boolean hidden) {
		if(!hidden) {
			dbgSplitPane.getItems().add(0, modulesBox);
			
			Divider div = dbgSplitPane.getDividers().get(0);
			div.setPosition(functionsDivPosition);
		}
		else {
			Divider div = dbgSplitPane.getDividers().get(0);

			functionsDivPosition = div.getPosition();
			
			div.setPosition(0d);
			dbgSplitPane.getItems().remove(0);
		}
	}

}
