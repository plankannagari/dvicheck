import { useEffect, useRef, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  SafeAreaView, ScrollView, ActivityIndicator,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';

import { COLORS } from '../constants';
import { uploadReceiptImage } from '../api/scanApi';
import useToastStore from '../store/toastStore';
import useHomeStore from '../store/homeStore';
import useHistoryStore from '../store/historyStore';
import Toast from '../components/Toast';

const CATEGORY_ORDER = ['ESSENTIAL', 'REDUCIBLE', 'AVOIDABLE', 'DUPLICATE'];

const CATEGORY_META = {
  ESSENTIAL: { label: 'Essential', dot: COLORS.green, bg: COLORS.greenLight, text: COLORS.green },
  REDUCIBLE: { label: 'Reducible', dot: COLORS.amber, bg: COLORS.amberLight, text: COLORS.amber },
  AVOIDABLE: { label: 'Avoidable', dot: COLORS.red, bg: COLORS.redLight, text: COLORS.red },
  DUPLICATE: { label: 'Duplicate', dot: COLORS.blue, bg: COLORS.blueLight, text: COLORS.blue },
};

const PROCESSING_STEPS = [
  'Reading receipt…',
  'Extracting items…',
  'Categorizing purchases…',
];

const fmt = (n) => n != null ? '$' + Number(n).toFixed(2) : '$0.00';
const fmtDate = (d) => d
  ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  : '';

function ItemRow({ item, isLast, manual }) {
  const meta = CATEGORY_META[item.category] || CATEGORY_META.ESSENTIAL;
  const showCategoryLabel = item.category && item.category !== 'ESSENTIAL';
  const showSuggestion = (item.category === 'REDUCIBLE' || item.category === 'AVOIDABLE') && !!item.suggestion;
  const showSavingBadge = Number(item.savingEstimate) > 0;

  return (
    <View style={[styles.itemRow, !isLast && styles.itemBorder]}>
      <View style={styles.categoryIndicator}>
        <View style={[styles.itemDot, { backgroundColor: meta.dot }]} />
        {showCategoryLabel && (
          <Text style={[styles.categoryLabel, { color: meta.text }]}>{item.category}</Text>
        )}
      </View>
      <View style={styles.itemMain}>
        <View style={styles.itemNameRow}>
          <Text style={styles.itemName} numberOfLines={1}>{item.name}</Text>
          {manual && (
            <View style={styles.manualBadge}>
              <Text style={styles.manualBadgeText}>manual</Text>
            </View>
          )}
        </View>
        {showSuggestion && (
          <Text style={styles.itemSuggestion} numberOfLines={2}>{item.suggestion}</Text>
        )}
      </View>
      <View style={styles.itemRightCol}>
        <Text style={styles.itemPrice}>{fmt(item.totalPrice)}</Text>
        {showSavingBadge && (
          <View style={styles.savingBadge}>
            <Text style={styles.savingBadgeText}>Save {fmt(item.savingEstimate)}</Text>
          </View>
        )}
      </View>
    </View>
  );
}

export default function ScanScreen({ navigation }) {
  const cameraRef = useRef(null);
  const [permission, requestPermission] = useCameraPermissions();
  const [phase, setPhase] = useState('camera'); // 'camera' | 'processing' | 'result'
  const [isCapturing, setIsCapturing] = useState(false);
  const [scanResult, setScanResult] = useState(null);
  const [stepIndex, setStepIndex] = useState(0);
  const [progress, setProgress] = useState(0);
  const [manualItems, setManualItems] = useState([]);
  const [manualName, setManualName] = useState('');
  const [manualPrice, setManualPrice] = useState('');
  const {
    visible: toastVisible, message: toastMessage, type: toastType, showToast, hideToast,
  } = useToastStore();
  const { loadDashboard } = useHomeStore();
  const { loadBills } = useHistoryStore();

  useEffect(() => {
    if (phase !== 'processing') return;
    setStepIndex(0);
    setProgress(0);

    const stepTimer = setInterval(() => {
      setStepIndex((i) => (i + 1) % PROCESSING_STEPS.length);
    }, 1200);

    const progressTimer = setInterval(() => {
      setProgress((p) => Math.min(p + 2.5, 100));
    }, 100);

    return () => {
      clearInterval(stepTimer);
      clearInterval(progressTimer);
    };
  }, [phase]);

  const handleScan = async (uri) => {
    setPhase('processing');
    try {
      const bill = await uploadReceiptImage(uri);
      setScanResult(bill);
      setPhase('result');
    } catch (err) {
      console.error('scan error:', err);
      const message = err.response?.data?.message
        || 'Could not read this receipt. Try again with better lighting.';
      showToast(message, 'error');
      setPhase('camera');
    }
  };

  const handleCapture = async () => {
    if (!cameraRef.current || isCapturing) return;
    setIsCapturing(true);
    try {
      const photo = await cameraRef.current.takePictureAsync({ quality: 0.7 });
      await handleScan(photo.uri);
    } catch (err) {
      console.error('capture error:', err);
      showToast('Could not capture photo. Try again.', 'error');
    } finally {
      setIsCapturing(false);
    }
  };

  const handlePickFromGallery = async () => {
    const permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permissionResult.granted) {
      showToast('Photo library access is needed to pick a receipt.', 'error');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
    });
    if (!result.canceled && result.assets?.length) {
      await handleScan(result.assets[0].uri);
    }
  };

  const reset = () => {
    setScanResult(null);
    setManualItems([]);
    setPhase('camera');
  };

  const addManualItem = () => {
    const name = manualName.trim();
    const price = parseFloat(manualPrice);
    if (!name || isNaN(price) || price <= 0) {
      showToast('Enter an item name and a valid price.', 'error');
      return;
    }
    const newItem = {
      name,
      totalPrice: price,
      unitPrice: price,
      category: 'ESSENTIAL',
      id: Date.now().toString(),
    };
    setManualItems((prev) => [...prev, newItem]);
    setManualName('');
    setManualPrice('');
  };

  if (phase === 'processing') {
    return (
      <View style={styles.processingScreen}>
        <Text style={styles.emoji}>🧾</Text>
        <Text style={styles.processingHeading}>Analysing receipt...</Text>
        <Text style={styles.processingStep}>{PROCESSING_STEPS[stepIndex]}</Text>
        <View style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${progress}%` }]} />
        </View>
        <Text style={styles.progressPercent}>{Math.round(progress)}%</Text>
      </View>
    );
  }

  if (phase === 'result' && scanResult) {
    const lineItems = [...(scanResult.lineItems ?? []), ...manualItems];
    const avoidableAmount = lineItems
      .filter((item) => item.category === 'AVOIDABLE' || item.category === 'REDUCIBLE')
      .reduce((sum, item) => sum + Number(item.totalPrice ?? 0), 0);
    const categoryCounts = CATEGORY_ORDER.reduce((acc, cat) => {
      acc[cat] = lineItems.filter((item) => item.category === cat).length;
      return acc;
    }, {});
    const avoidableOrReducibleCount = categoryCounts.AVOIDABLE + categoryCounts.REDUCIBLE;

    return (
      <SafeAreaView style={styles.resultSafeArea}>
        <ScrollView contentContainerStyle={styles.resultScroll}>
          <View style={styles.resultCard}>
            <Text style={styles.storeName}>{scanResult.storeName}</Text>
            <Text style={styles.billDate}>{fmtDate(scanResult.purchaseDate)}</Text>
            <Text style={styles.totalAmount}>{fmt(scanResult.totalAmount)}</Text>
          </View>

          {avoidableAmount > 0 && (
            <View style={styles.savingsCard}>
              <Text style={styles.savingsCardAmount}>{fmt(avoidableAmount)}</Text>
              <Text style={styles.savingsCardLabel}>potential savings this shop</Text>
              <Text style={styles.savingsCardSubtext}>
                {avoidableOrReducibleCount} avoidable or reducible item{avoidableOrReducibleCount !== 1 ? 's' : ''}
              </Text>
            </View>
          )}

          <View style={styles.pillsRow}>
            {CATEGORY_ORDER.map((cat) => {
              const meta = CATEGORY_META[cat];
              return (
                <View key={cat} style={[styles.pill, { backgroundColor: meta.bg }]}>
                  <View style={[styles.pillDot, { backgroundColor: meta.dot }]} />
                  <Text style={[styles.pillText, { color: meta.text }]}>
                    {meta.label} {categoryCounts[cat]}
                  </Text>
                </View>
              );
            })}
          </View>

          {lineItems.length > 0 && (
            <View style={styles.itemsCard}>
              {lineItems.map((item, i) => (
                <ItemRow key={item.id ?? i} item={item} isLast={i === lineItems.length - 1} />
              ))}
            </View>
          )}

          <View style={styles.manualSection}>
            <Text style={styles.manualSectionLabel}>Add missed items</Text>
            <View style={styles.manualInputRow}>
              <TextInput
                style={[styles.manualInput, styles.manualNameInput]}
                placeholder="Item name"
                placeholderTextColor={COLORS.inkFaint}
                value={manualName}
                onChangeText={setManualName}
                returnKeyType="done"
              />
              <TextInput
                style={[styles.manualInput, styles.manualPriceInput]}
                placeholder="0.00"
                placeholderTextColor={COLORS.inkFaint}
                value={manualPrice}
                onChangeText={setManualPrice}
                keyboardType="decimal-pad"
                returnKeyType="done"
                onSubmitEditing={addManualItem}
              />
              <TouchableOpacity style={styles.manualAddBtn} onPress={addManualItem} activeOpacity={0.8}>
                <Text style={styles.manualAddBtnText}>Add</Text>
              </TouchableOpacity>
            </View>

            {manualItems.length > 0 && (
              <View style={styles.itemsCard}>
                {manualItems.map((item, i) => (
                  <ItemRow key={item.id} item={item} isLast={i === manualItems.length - 1} manual />
                ))}
              </View>
            )}
          </View>

          <TouchableOpacity
            style={styles.primaryBtn}
            onPress={() => {
              // TODO Day 15: send manualItems to backend PATCH endpoint
              loadDashboard();
              loadBills(true);
              reset();
              navigation.navigate('History');
            }}
            activeOpacity={0.8}
          >
            <Text style={styles.primaryBtnText}>Save and Done</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryBtn} onPress={reset} activeOpacity={0.8}>
            <Text style={styles.secondaryBtnText}>Scan another</Text>
          </TouchableOpacity>
        </ScrollView>
        <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
      </SafeAreaView>
    );
  }

  // phase === 'camera'
  if (!permission) {
    return <View style={styles.cameraScreen} />;
  }

  if (!permission.granted) {
    return (
      <SafeAreaView style={styles.centerScreen}>
        <Text style={styles.emoji}>📷</Text>
        <Text style={styles.heading}>Camera access needed</Text>
        <Text style={styles.subtext}>DviCheck needs your camera to scan receipts.</Text>
        <TouchableOpacity style={styles.primaryBtn} onPress={requestPermission} activeOpacity={0.8}>
          <Text style={styles.primaryBtnText}>Allow camera access</Text>
        </TouchableOpacity>
      </SafeAreaView>
    );
  }

  return (
    <View style={styles.cameraScreen}>
      <CameraView ref={cameraRef} style={styles.camera} facing="back" />

      <SafeAreaView style={styles.cameraOverlay} pointerEvents="box-none">
        <Text style={styles.cameraHint}>Position receipt within frame</Text>

        <View style={styles.guideBox} pointerEvents="none">
          <View style={[styles.corner, styles.cornerTopLeft]} />
          <View style={[styles.corner, styles.cornerTopRight]} />
          <View style={[styles.corner, styles.cornerBottomLeft]} />
          <View style={[styles.corner, styles.cornerBottomRight]} />
        </View>

        <View style={styles.bottomBar}>
          <TouchableOpacity style={styles.galleryBtn} onPress={handlePickFromGallery} activeOpacity={0.8}>
            <Text style={styles.galleryIcon}>🖼️</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.captureBtn}
            onPress={handleCapture}
            activeOpacity={0.8}
            disabled={isCapturing}
          >
            {isCapturing
              ? <ActivityIndicator color={COLORS.accent} />
              : null}
          </TouchableOpacity>

          <View style={styles.spacer} />
        </View>
      </SafeAreaView>
      <Toast visible={toastVisible} message={toastMessage} type={toastType} onHide={hideToast} />
    </View>
  );
}

const styles = StyleSheet.create({
  centerScreen: {
    flex: 1, backgroundColor: COLORS.bg,
    alignItems: 'center', justifyContent: 'center', padding: 28,
  },
  emoji: { fontSize: 48, marginBottom: 16 },
  heading: { fontSize: 20, color: COLORS.ink, fontWeight: '600', marginBottom: 6, textAlign: 'center' },
  subtext: { fontSize: 13, color: COLORS.inkLight, textAlign: 'center', marginBottom: 20 },
  primaryBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 14, paddingHorizontal: 28, marginTop: 8,
  },
  primaryBtnText: { color: '#fff', fontSize: 15, fontWeight: '600', textAlign: 'center' },
  secondaryBtn: { paddingVertical: 14, paddingHorizontal: 28, marginTop: 4 },
  secondaryBtnText: { color: COLORS.inkLight, fontSize: 14, textAlign: 'center' },

  // Camera phase
  cameraScreen: { flex: 1, backgroundColor: '#000' },
  camera: { ...StyleSheet.absoluteFillObject },
  cameraOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    paddingTop: 20,
  },
  cameraHint: {
    color: '#fff', fontSize: 13, backgroundColor: 'rgba(0,0,0,0.4)',
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20,
  },
  guideBox: {
    width: '82%', height: '42%', marginTop: '16%',
    position: 'relative',
  },
  corner: {
    position: 'absolute', width: 28, height: 28, borderColor: COLORS.accent,
  },
  cornerTopLeft: { top: 0, left: 0, borderTopWidth: 3, borderLeftWidth: 3, borderTopLeftRadius: 8 },
  cornerTopRight: { top: 0, right: 0, borderTopWidth: 3, borderRightWidth: 3, borderTopRightRadius: 8 },
  cornerBottomLeft: { bottom: 0, left: 0, borderBottomWidth: 3, borderLeftWidth: 3, borderBottomLeftRadius: 8 },
  cornerBottomRight: { bottom: 0, right: 0, borderBottomWidth: 3, borderRightWidth: 3, borderBottomRightRadius: 8 },

  bottomBar: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: 'rgba(0,0,0,0.55)',
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 32, paddingTop: 20, paddingBottom: 36,
  },
  galleryBtn: {
    width: 48, height: 48, borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center', justifyContent: 'center',
  },
  galleryIcon: { fontSize: 22 },
  captureBtn: {
    width: 72, height: 72, borderRadius: 36,
    backgroundColor: '#fff',
    alignItems: 'center', justifyContent: 'center',
  },
  spacer: { width: 48, height: 48 },

  // Processing phase
  processingScreen: {
    flex: 1, backgroundColor: '#000',
    alignItems: 'center', justifyContent: 'center', padding: 28,
  },
  processingHeading: { fontSize: 18, color: '#fff', fontWeight: '600', marginTop: 4 },
  processingStep: { fontSize: 13, color: 'rgba(255,255,255,0.6)', marginTop: 10, marginBottom: 6 },
  progressTrack: {
    width: '70%', height: 6, borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.15)', overflow: 'hidden', marginTop: 16,
  },
  progressFill: { height: '100%', backgroundColor: COLORS.accent, borderRadius: 3 },
  progressPercent: { fontSize: 12, color: 'rgba(255,255,255,0.7)', marginTop: 8 },

  // Result phase
  resultSafeArea: { flex: 1, backgroundColor: COLORS.bg },
  resultScroll: { padding: 24, alignItems: 'center' },
  resultCard: {
    width: '100%', backgroundColor: COLORS.card, borderRadius: 16,
    padding: 20, alignItems: 'center', marginBottom: 16,
    borderWidth: 1, borderColor: COLORS.border,
  },
  storeName: { fontSize: 17, color: COLORS.ink, fontWeight: '600', marginBottom: 4 },
  billDate: { fontSize: 12, color: COLORS.inkLight, marginBottom: 12 },
  totalAmount: { fontSize: 32, color: COLORS.accent, fontWeight: '300' },

  savingsCard: {
    width: '100%', backgroundColor: COLORS.greenLight, borderRadius: 16,
    padding: 20, alignItems: 'center', marginBottom: 16,
  },
  savingsCardAmount: { fontSize: 32, color: COLORS.green, fontWeight: '700' },
  savingsCardLabel: { fontSize: 13, color: COLORS.green, fontWeight: '600', marginTop: 4 },
  savingsCardSubtext: { fontSize: 12, color: COLORS.inkLight, marginTop: 6 },

  pillsRow: {
    flexDirection: 'row', flexWrap: 'wrap', gap: 8,
    width: '100%', marginBottom: 16,
  },
  pill: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16,
  },
  pillDot: { width: 8, height: 8, borderRadius: 4, marginRight: 6 },
  pillText: { fontSize: 11, fontWeight: '600' },

  itemsCard: {
    width: '100%', backgroundColor: COLORS.card, borderRadius: 16,
    overflow: 'hidden', borderWidth: 1, borderColor: COLORS.border, marginBottom: 24,
  },
  itemRow: {
    flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between',
    padding: 14, paddingHorizontal: 16, gap: 12,
  },
  itemBorder: { borderBottomWidth: 1, borderBottomColor: COLORS.border },
  categoryIndicator: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingTop: 2 },
  itemDot: { width: 8, height: 8, borderRadius: 4 },
  categoryLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 0.3 },
  itemMain: { flex: 1 },
  itemNameRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  itemName: { fontSize: 13, color: COLORS.ink, flexShrink: 1 },
  itemSuggestion: { fontSize: 12, fontStyle: 'italic', color: COLORS.inkLight, marginTop: 3 },
  itemRightCol: { alignItems: 'flex-end', paddingTop: 2 },
  itemPrice: { fontSize: 13, color: COLORS.ink, fontWeight: '600' },
  savingBadge: {
    backgroundColor: COLORS.greenLight, borderRadius: 10,
    paddingHorizontal: 8, paddingVertical: 3, marginTop: 4,
  },
  savingBadgeText: { fontSize: 10, color: COLORS.green, fontWeight: '700' },

  manualSection: { width: '100%', marginBottom: 16 },
  manualSectionLabel: {
    fontSize: 10, color: COLORS.inkLight, letterSpacing: 2,
    textTransform: 'uppercase', marginBottom: 10,
  },
  manualInputRow: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10,
  },
  manualInput: {
    backgroundColor: COLORS.card, borderRadius: 12,
    borderWidth: 1, borderColor: COLORS.border,
    paddingHorizontal: 12, paddingVertical: 10, fontSize: 13, color: COLORS.ink,
  },
  manualNameInput: { flex: 1 },
  manualPriceInput: { width: 80, textAlign: 'right' },
  manualAddBtn: {
    backgroundColor: COLORS.accent, borderRadius: 12,
    paddingVertical: 11, paddingHorizontal: 16,
  },
  manualAddBtnText: { color: '#fff', fontSize: 13, fontWeight: '600' },
  manualBadge: {
    backgroundColor: COLORS.border, borderRadius: 10,
    paddingHorizontal: 8, paddingVertical: 3,
  },
  manualBadgeText: { fontSize: 10, color: COLORS.inkLight, fontWeight: '600' },
});
