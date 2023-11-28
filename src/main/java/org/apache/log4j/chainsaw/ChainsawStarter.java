package org.apache.log4j.chainsaw;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.log4j.chainsaw.osx.OSXIntegration;
import org.apache.log4j.chainsaw.prefs.SettingsManager;
import org.apache.log4j.chainsaw.components.splash.SplashViewer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.util.Locale;

public class ChainsawStarter {
    private static Logger logger = LogManager.getLogger(ChainsawStarter.class);



    /**
     * Starts Chainsaw by attaching a new instance to the Log4J main root Logger
     * via a ChainsawAppender, and activates itself
     *
     * @param args
     */
    public static void main(String[] args) {
        if (OSXIntegration.IS_OSX) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        AbstractConfiguration configuration = SettingsManager.getInstance().getGlobalConfiguration();

        EventQueue.invokeLater(() -> {
            String lookAndFeelClassName = configuration.getString("lookAndFeelClassName");
            if (lookAndFeelClassName == null || lookAndFeelClassName.trim().isEmpty()) {
                String osName = System.getProperty("os.name");
                if (osName.toLowerCase(Locale.ENGLISH).startsWith("mac")) {
                    //no need to assign look and feel
                } else if (osName.toLowerCase(Locale.ENGLISH).startsWith("windows")) {
                    lookAndFeelClassName = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
                    configuration.setProperty("lookAndFeelClassName",lookAndFeelClassName);
                } else if (osName.toLowerCase(Locale.ENGLISH).startsWith("linux")) {
                    lookAndFeelClassName = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
                    configuration.setProperty("lookAndFeelClassName",lookAndFeelClassName);
                }
            }

            if (lookAndFeelClassName != null && !(lookAndFeelClassName.trim().isEmpty())) {
                try{
                    UIManager.setLookAndFeel(lookAndFeelClassName);
                }catch(Exception ex){}
            }else{
                try{
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }catch(Exception ex){}
            }
            createChainsawGUI(null);
        });
    }

    /**
     * Creates, activates, and then shows the Chainsaw GUI, optionally showing
     * the splash screen, and using the passed shutdown action when the user
     * requests to exit the application (if null, then Chainsaw will exit the vm)
     *
     * @param newShutdownAction DOCUMENT ME!
     */
    public static void createChainsawGUI(Action newShutdownAction) {
        AbstractConfiguration config = SettingsManager.getInstance().getGlobalConfiguration();
        SplashViewer splashViewer = new SplashViewer();

        if (config.getBoolean("okToRemoveSecurityManager", false)) {
            System.setSecurityManager(null);
            // this SHOULD set the Policy/Permission stuff for any
            // code loaded from our custom classloader.
            // crossing fingers...
            Policy.setPolicy(new Policy() {
                @Override
                public PermissionCollection getPermissions(CodeSource codesource) {
                    Permissions perms = new Permissions();
                    perms.add(new AllPermission());
                    return (perms);
                }
            });
        }

        final LogUI logUI = new LogUI();
        final LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        logUI.chainsawAppender = ctx.getConfiguration().getAppender("chainsaw");

        if (config.getBoolean("slowSplash", true)) {
            splashViewer.showSplash(logUI);
        }
        logUI.cyclicBufferSize = config.getInt("cyclicBufferSize", 50000);


        /**
         * TODO until we work out how JoranConfigurator might be able to have
         * configurable class loader, if at all.  For now we temporarily replace the
         * TCCL so that Plugins that need access to resources in
         * the Plugins directory can find them (this is particularly
         * important for the Web start version of Chainsaw
         */
        //configuration initialized here

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logger.error("Uncaught exception in thread {}", t.getName(), e);
        });

        EventQueue.invokeLater(() -> {
            logUI.activateViewer();
            splashViewer.removeSplash();
        });
        EventQueue.invokeLater(logUI::buildChainsawLogPanel);

        logger.info("SecurityManager is now: {}", System.getSecurityManager());

        if (newShutdownAction != null) {
            logUI.setShutdownAction(newShutdownAction);
        } else {
            logUI.setShutdownAction(
                new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        System.exit(0);
                    }
                });
        }
    }
}
