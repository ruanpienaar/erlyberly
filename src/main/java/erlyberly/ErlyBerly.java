package erlyberly;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.SplitPane.Divider;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import erlyberly.node.NodeAPI;

import com.ericsson.otp.erlang.OtpErlangException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ErlyBerly extends Application {

	private static final NodeAPI nodeAPI = new NodeAPI();

	private SplitPane splitPane;

	private Region entopPane;

	private double entopDivPosition;

	public static void main(String[] args) throws Exception {
		launch(args);
	}

    @Override
    public void start(Stage primaryStage) throws IOException {

        try {
			PrefBind.setup();
		} catch (IOException e) {
			e.printStackTrace();
		}

		FxmlLoadable topBarFxml;

		topBarFxml = new FxmlLoadable("/erlyberly/topbar.fxml");
		topBarFxml.load();

		FxmlLoadable dbgFxml;

		dbgFxml = new FxmlLoadable("/erlyberly/dbg.fxml");
		dbgFxml.load();

		splitPane = new SplitPane();

		entopPane = (Region) loadEntopPane();
		splitPane.getItems().add(entopPane);

		splitPane.getItems().add(dbgFxml.load());

		setupProcPaneHiding(topBarFxml, dbgFxml);

		VBox rootView;

		rootView = new VBox(topBarFxml.fxmlNode, splitPane);
		rootView.setMaxWidth(Double.MAX_VALUE);

		VBox.setVgrow(splitPane, Priority.ALWAYS);

        Scene scene;

		scene = new Scene(rootView);
		applyCssToWIndow(scene);

		primaryStage.setScene(scene);
        primaryStage.titleProperty().bind(nodeAPI.summaryProperty());
        primaryStage.sizeToScene();
        primaryStage.setResizable(true);
        primaryStage.show();

        displayConnectionPopup(primaryStage);
        FilterFocusManager.init(scene);
        
        // -------------------------------------------
        // CMD line argument checking!

        // TODO: my first attempt at getting commons-cli to work
        //       with String[] args.
        Parameters parameters = getParameters();
        List<String> rawArguments = parameters.getRaw();
        String[] cmlineArgs = rawArguments.toArray(new String[0]);

        Options options = new Options();
        Option module = OptionBuilder.withArgName("module_name")
            .hasArg()
            .withDescription("module used to trace")
            .create("module");
        options.addOption(module);

        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse( options, cmlineArgs );

            if( line.hasOption( "module" ) ) {
                // TODO: please check if module and function and arity is present....

                //System.out.println( "module > " + line.getOptionValue( "module" ) + " <" );

                //!!! - HERE CALL THE DBG controller, and add the module to the trace list....


                // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

                FXMLLoader loader = new FXMLLoader(
                  getClass().getResource(
                    "/erlyberly/dbg.fxml"
                  )
                );

                SplitPane pane = (SplitPane) loader.load();

                DbgView controller =
                    loader.<DbgView>getController();
                //controller.initData(customer);

                ModFunc mf = new ModFunc("inet_db", "module_info", 0, false, false);
                // controller.dbgController.traceModFunc(mf);
                
                controller.dbgController.storeTraceModFunc(mf);
                
                controller.dbgController.setCollectingTraces(true);
                System.out.println(controller.hashCode());

                // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


            }

        } catch( ParseException exp ) {
            // oops, something went wrong
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );

            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "erlyberly-0.6.9-SNAPSHOT-runnable.jar", options );

            System.exit(0);
        }

        // -------------------------------------------
    }

    public static void applyCssToWIndow(Scene scene) {
        scene.getStylesheets().add(ErlyBerly.class.getResource("/floatyfield/floaty-field.css").toExternalForm());
		scene.getStylesheets().add(ErlyBerly.class.getResource("/erlyberly/erlyberly.css").toString());
    }

	private void setupProcPaneHiding(FxmlLoadable topBarFxml, FxmlLoadable dbgFxml) {
		TopBarView topView;
		DbgView dbgView;

		topView = (TopBarView)topBarFxml.controller;
		dbgView = (DbgView) dbgFxml.controller;

		topView.hideProcsProperty().addListener((ObservableValue<? extends Boolean> o, Boolean ob, Boolean nb) -> {
			if(!nb) {
				splitPane.getItems().add(0, entopPane);

				Divider div = splitPane.getDividers().get(0);
				div.setPosition(entopDivPosition);
			}
			else {
				Divider div = splitPane.getDividers().get(0);

				entopDivPosition = div.getPosition();

				div.setPosition(0d);
				splitPane.getItems().remove(0);
			}
		});

		topView.hideFunctionsProperty().addListener((ObservableValue<? extends Boolean> o, Boolean ob, Boolean nb) -> {
			dbgView.setFunctionsVisibility(nb);
		});

		topView.setOnRefreshModules(dbgView::onRefreshModules);

		Platform.runLater(() -> { topView.addAccelerators(); });
	}

	private Parent loadEntopPane() {
		Parent entopPane = new FxmlLoadable("/erlyberly/entop.fxml").load();
        SplitPane.setResizableWithParent(entopPane, Boolean.FALSE);

		return entopPane;
	}

	private void displayConnectionPopup(Stage primaryStage) {
		Stage connectStage;

		connectStage = new Stage();
        connectStage.initModality(Modality.WINDOW_MODAL);
        connectStage.setScene(new Scene(new FxmlLoadable("/erlyberly/connection.fxml").load()));
        connectStage.setAlwaysOnTop(true);

        // javafx vertical resizing is laughably ugly, lets just disallow it
        connectStage.setResizable(false);
        connectStage.setWidth(400);

        // if the user closes the window without connecting then close the app
        connectStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent e) {
				if(!nodeAPI.connectedProperty().get()) {
					Platform.exit();
				}

				Platform.runLater(() -> { primaryStage.setResizable(true); });
			}});

        connectStage.show();
	}

	public static NodeAPI nodeAPI() {
		return nodeAPI;
	}


    public static void subWindow(String title, Parent parentControl) {
        Stage stage = new Stage();
        Scene scene = new Scene(parentControl);

        CloseWindowOnEscape.apply(scene, stage);

        stage.setScene(scene);
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setTitle(title);

        stage.show();
    }

    public static void showSourceCodeWindow(String title, String moduleSourceCode) {
        assert Platform.isFxApplicationThread();

        TextArea textArea;

        textArea = new TextArea(moduleSourceCode);
        textArea.getStyleClass().add("mod-src");
        textArea.setEditable(false);

        Scene scene = new Scene(textArea, 800, 800);
        ErlyBerly.applyCssToWIndow(scene);

        Stage primaryStage;

        primaryStage = new Stage();
        primaryStage.setTitle(title);
        primaryStage.setScene(scene);
        primaryStage.show();

        CloseWindowOnEscape.apply(scene, primaryStage);
    }
}
