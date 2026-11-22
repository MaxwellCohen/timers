import { Inter_400Regular, Inter_500Medium, Inter_600SemiBold } from '@expo-google-fonts/inter';
import { SpaceGrotesk_600SemiBold, SpaceGrotesk_700Bold } from '@expo-google-fonts/space-grotesk';
import { useFonts } from 'expo-font';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import HomeScreen from '@/app/pages/HomeScreen';
import SettingsScreen from '@/app/pages/SettingsScreen';
import { Colors } from '@/constants/theme';
import { usePreferences } from '@/hooks/use-preferences';

SplashScreen.preventAutoHideAsync();

export default function App() {
  const [loaded, error] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    SpaceGrotesk_600SemiBold,
    SpaceGrotesk_700Bold,
  });
  const [screen, setScreen] = useState<'home' | 'settings'>('home');
  const [preferences, updatePreferences] = usePreferences();
  // On a font error, render anyway with the system font rather than a blank screen.
  const ready = loaded || error != null;

  useEffect(() => {
    if (ready) SplashScreen.hide();
  }, [ready]);

  if (!ready) return null;

  return (
    <SafeAreaProvider>
      <View style={styles.root}>
        {/* Home stays mounted under Settings so the typed entry survives the visit. */}
        <View style={screen === 'home' ? styles.root : styles.hidden}>
          <HomeScreen preferences={preferences} onOpenSettings={() => setScreen('settings')} />
        </View>
        {screen === 'settings' && (
          <SettingsScreen
            preferences={preferences}
            onChangePreferences={updatePreferences}
            onBack={() => setScreen('home')}
          />
        )}
      </View>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  hidden: {
    display: 'none',
  },
});
