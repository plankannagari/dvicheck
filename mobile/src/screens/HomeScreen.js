import React, { useEffect } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity,
  StyleSheet, StatusBar, SafeAreaView,
  RefreshControl, ActivityIndicator
} from 'react-native';
import useAuthStore from '../store/authStore';
import useHomeStore from '../store/homeStore';
import { COLORS } from '../constants';

const fmt = (n) => n != null ? '$' + Number(n).toFixed(2) : '$0.00';
const fmtDate = (d) => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
const billIcon = (type) => type === 'UTILITY' ? '⚡' : '🛒';

function SkeletonBox({ height = 20, width = '100%', style }) {
  return <View style={[{ height, width, borderRadius: 8, backgroundColor: COLORS.border }, style]} />;
}

export default function HomeScreen({ navigation }) {
  const { user } = useAuthStore();
  const { summary, recentBills, isLoading, error, loadDashboard } = useHomeStore();

  useEffect(() => { loadDashboard(); }, []);

  const isFirstLoad = isLoading && summary === null;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={COLORS.bg} />

      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Good morning</Text>
          <Text style={styles.phone}>{user?.phone || ''}</Text>
        </View>
        <TouchableOpacity onPress={() => navigation.navigate('Profile')}>
          <Text style={styles.gearIcon}>⚙️</Text>
        </TouchableOpacity>
      </View>

      {/* Error banner */}
      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}

      <ScrollView
        style={styles.scroll}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={isLoading}
            onRefresh={loadDashboard}
            tintColor={COLORS.accent}
          />
        }
      >
        {/* Hero savings card */}
        <View style={styles.heroCard}>
          {isFirstLoad ? (
            <View style={{ gap: 12 }}>
              <SkeletonBox height={14} width="60%" />
              <SkeletonBox height={44} width="50%" />
              <SkeletonBox height={12} width="70%" />
            </View>
          ) : (
            <>
              <Text style={styles.heroLabel}>THIS MONTH YOU COULD SAVE</Text>
              <Text style={styles.heroAmount}>{fmt(summary?.avoidableSpend)}</Text>
              <Text style={styles.heroSub}>
                {summary?.billsScanned > 0
                  ? 'Based on ' + summary.billsScanned + ' scans this month'
                  : 'Scan your first receipt to get started'}
              </Text>
              <TouchableOpacity
                style={styles.scanBtn}
                onPress={() => navigation.navigate('Scan')}
                activeOpacity={0.8}
              >
                <Text style={styles.scanBtnText}>+ Scan a receipt</Text>
              </TouchableOpacity>
            </>
          )}
        </View>

        {/* Quick stats */}
        <View style={styles.statsRow}>
          {isFirstLoad ? (
            [1,2,3].map(i => (
              <View key={i} style={[styles.statCard, { gap: 8 }]}>
                <SkeletonBox height={20} width={32} />
                <SkeletonBox height={24} width={40} />
                <SkeletonBox height={10} width="80%" />
              </View>
            ))
          ) : (
            [
              { label:'Bills scanned', value: summary?.billsScanned ?? 0, icon:'🧾' },
              { label:'Duplicates caught', value: summary?.duplicatesCaught ?? 0, icon:'🔁' },
              { label:'Saved this month', value: fmt(summary?.estimatedSaved), icon:'💰' },
            ].map(s => (
              <View key={s.label} style={styles.statCard}>
                <Text style={styles.statIcon}>{s.icon}</Text>
                <Text style={styles.statValue}>{s.value}</Text>
                <Text style={styles.statLabel}>{s.label}</Text>
              </View>
            ))
          )}
        </View>

        {/* Suggestion card */}
        <View style={styles.suggestionCard}>
          <Text style={styles.suggestionLabel}>💡 TOP SUGGESTION THIS WEEK</Text>
          <Text style={styles.suggestionText}>
            {summary?.topSuggestion ?? 'Scan receipts to get personalised AI suggestions'}
          </Text>
        </View>

        {/* Recent bills */}
        <Text style={styles.sectionLabel}>Recent bills</Text>
        <View style={styles.billsCard}>
          {isFirstLoad ? (
            [1,2,3].map(i => (
              <View key={i} style={[styles.billRow, i < 3 && styles.billBorder, { gap: 12 }]}>
                <SkeletonBox height={44} width={44} style={{ borderRadius: 12 }} />
                <View style={{ flex:1, gap:6 }}>
                  <SkeletonBox height={14} width="60%" />
                  <SkeletonBox height={11} width="40%" />
                </View>
                <View style={{ alignItems:'flex-end', gap:6 }}>
                  <SkeletonBox height={14} width={56} />
                  <SkeletonBox height={11} width={40} />
                </View>
              </View>
            ))
          ) : recentBills.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyIcon}>🧾</Text>
              <Text style={styles.emptyText}>No bills yet</Text>
              <Text style={styles.emptySub}>Tap Scan to add your first receipt</Text>
            </View>
          ) : (
            recentBills.map((b, i) => (
              <View
                key={b.id}
                style={[styles.billRow, i < recentBills.length-1 && styles.billBorder]}
              >
                <View style={styles.billIconBox}>
                  <Text style={styles.billIconText}>{billIcon(b.billType)}</Text>
                </View>
                <View style={styles.billMeta}>
                  <Text style={styles.billStore}>{b.storeName}</Text>
                  <Text style={styles.billDate}>{fmtDate(b.purchaseDate)}</Text>
                </View>
                <View style={styles.billAmounts}>
                  <Text style={styles.billTotal}>{fmt(b.totalAmount)}</Text>
                  {Number(b.avoidableAmount) > 0 && (
                    <Text style={styles.billAvoidable}>-{fmt(b.avoidableAmount)}</Text>
                  )}
                </View>
              </View>
            ))
          )}
        </View>

        <View style={{ height: 20 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea:{ flex:1, backgroundColor:COLORS.bg },
  header:{
    flexDirection:'row', justifyContent:'space-between',
    alignItems:'center', padding:16, paddingHorizontal:24,
    backgroundColor:COLORS.bg,
  },
  greeting:{ fontSize:12, color:COLORS.inkLight, letterSpacing:0.5 },
  phone:{ fontSize:16, color:COLORS.ink },
  gearIcon:{ fontSize:22 },
  errorBanner:{
    backgroundColor:COLORS.redLight, padding:10, paddingHorizontal:20,
    borderBottomWidth:1, borderBottomColor:COLORS.red+'44',
  },
  errorText:{ fontSize:12, color:COLORS.red },
  scroll:{ flex:1 },
  scrollContent:{ paddingBottom:20 },
  heroCard:{
    margin:16, borderRadius:20,
    backgroundColor:COLORS.ink, padding:24,
  },
  heroLabel:{
    fontSize:9, color:'rgba(255,255,255,0.4)',
    letterSpacing:2, marginBottom:8,
  },
  heroAmount:{
    fontSize:42, color:COLORS.accent,
    fontWeight:'300', letterSpacing:-1, marginBottom:4,
  },
  heroSub:{ fontSize:12, color:'rgba(255,255,255,0.3)', marginBottom:20 },
  scanBtn:{
    backgroundColor:COLORS.accent, borderRadius:12,
    paddingVertical:12, paddingHorizontal:20, alignSelf:'flex-start',
  },
  scanBtnText:{ color:'#fff', fontSize:14, fontWeight:'600' },
  statsRow:{
    flexDirection:'row', gap:10,
    marginHorizontal:16, marginBottom:16,
  },
  statCard:{
    flex:1, backgroundColor:COLORS.card, borderRadius:14,
    padding:14, alignItems:'center',
    borderWidth:1, borderColor:COLORS.border,
    shadowColor:'#000', shadowOffset:{width:0,height:2},
    shadowOpacity:0.06, shadowRadius:8, elevation:2,
  },
  statIcon:{ fontSize:20, marginBottom:6 },
  statValue:{ fontSize:20, color:COLORS.ink, fontWeight:'600', marginBottom:2 },
  statLabel:{ fontSize:9, color:COLORS.inkLight, textAlign:'center', lineHeight:13 },
  suggestionCard:{
    marginHorizontal:16, marginBottom:16,
    backgroundColor:COLORS.amberLight, borderRadius:16,
    padding:16, borderWidth:1, borderColor:'rgba(184,124,10,0.2)',
  },
  suggestionLabel:{
    fontSize:9, color:COLORS.amber, letterSpacing:2, marginBottom:8,
  },
  suggestionText:{ fontSize:13, color:COLORS.ink, lineHeight:20 },
  sectionLabel:{
    fontSize:10, color:COLORS.inkLight, letterSpacing:2,
    textTransform:'uppercase', marginHorizontal:16, marginBottom:10,
  },
  billsCard:{
    marginHorizontal:16, backgroundColor:COLORS.card,
    borderRadius:16, overflow:'hidden',
    borderWidth:1, borderColor:COLORS.border,
  },
  billRow:{
    flexDirection:'row', alignItems:'center',
    padding:14, paddingHorizontal:16, gap:12,
  },
  billBorder:{ borderBottomWidth:1, borderBottomColor:COLORS.border },
  billIconBox:{
    width:44, height:44, borderRadius:12,
    backgroundColor:COLORS.bg, alignItems:'center', justifyContent:'center',
  },
  billIconText:{ fontSize:20 },
  billMeta:{ flex:1 },
  billStore:{ fontSize:14, color:COLORS.ink },
  billDate:{ fontSize:11, color:COLORS.inkLight, marginTop:2 },
  billAmounts:{ alignItems:'flex-end' },
  billTotal:{ fontSize:15, color:COLORS.ink, fontWeight:'600' },
  billAvoidable:{ fontSize:11, color:COLORS.red, marginTop:2 },
  emptyState:{
    padding:32, alignItems:'center',
  },
  emptyIcon:{ fontSize:40, marginBottom:12 },
  emptyText:{ fontSize:16, color:COLORS.ink, marginBottom:6 },
  emptySub:{ fontSize:13, color:COLORS.inkLight, textAlign:'center' },
});
