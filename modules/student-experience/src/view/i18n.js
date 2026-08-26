/**
 * Deliberately flat and small: this is the navigation shell, dashboards and auth page, not a
 * translation of every form in the app. New keys extend the same two objects — nothing about
 * the shape needs to change to grow this later (see docs/STATE_CONTRACT.md-style scoping: land
 * the infra once, widen coverage in follow-ups rather than block the toggle on 100% coverage).
 */
const STRINGS = {
  en: {
    'lang.en': 'EN',
    'lang.hi': 'हिं',
    'workspace.student': 'Student Workspace',
    'workspace.staff': 'Staff Workspace',
    'action.signOut': 'Sign out',
    'action.signIn': 'Sign in',
    'action.seeAll': 'See all',
    'action.search': 'Search',
    'action.allNotifications': 'All notifications',

    'nav.dashboard': 'Dashboard',
    'nav.myRequests': 'My Requests',
    'nav.leave': 'Leave',
    'nav.internship': 'Internship',
    'nav.documents': 'Documents',
    'nav.academic': 'Academic',
    'nav.grievance': 'Grievance',
    'nav.myTasks': 'My Tasks',
    'nav.students': 'Students',
    'nav.attendance': 'Attendance',
    'nav.marksResults': 'Marks & Results',
    'nav.notifications': 'Notifications',

    'section.keyMetrics': 'Key metrics',
    'section.quickActions': 'Quick actions',
    'section.recentRequests': 'Recent requests',
    'section.waitingOnYou': 'Waiting on you',
    'section.recentActivity': 'Recent activity',
    'section.needsAttention': 'Needs your attention',

    'login.title': 'CampusOS',
    'login.subtitle': 'One sign-in for students and staff',
    'login.username': 'Username',
    'login.password': 'Password',
  },
  hi: {
    'lang.en': 'EN',
    'lang.hi': 'हिं',
    'workspace.student': 'छात्र कार्यक्षेत्र',
    'workspace.staff': 'स्टाफ़ कार्यक्षेत्र',
    'action.signOut': 'साइन आउट करें',
    'action.signIn': 'साइन इन करें',
    'action.seeAll': 'सभी देखें',
    'action.search': 'खोजें',
    'action.allNotifications': 'सभी सूचनाएं',

    'nav.dashboard': 'डैशबोर्ड',
    'nav.myRequests': 'मेरे अनुरोध',
    'nav.leave': 'छुट्टी',
    'nav.internship': 'इंटर्नशिप',
    'nav.documents': 'दस्तावेज़',
    'nav.academic': 'शैक्षणिक',
    'nav.grievance': 'शिकायत',
    'nav.myTasks': 'मेरे कार्य',
    'nav.students': 'छात्र सूची',
    'nav.attendance': 'उपस्थिति',
    'nav.marksResults': 'अंक व परिणाम',
    'nav.notifications': 'सूचनाएं',

    'section.keyMetrics': 'मुख्य आंकड़े',
    'section.quickActions': 'त्वरित कार्य',
    'section.recentRequests': 'हाल के अनुरोध',
    'section.waitingOnYou': 'आपकी प्रतीक्षा में',
    'section.recentActivity': 'हाल की गतिविधि',
    'section.needsAttention': 'आपके ध्यान की आवश्यकता है',

    'login.title': 'कैंपसओएस',
    'login.subtitle': 'छात्रों और स्टाफ़ के लिए एक ही साइन-इन',
    'login.username': 'यूज़रनेम',
    'login.password': 'पासवर्ड',
  },
};

export const I18n = {
  SUPPORTED: Object.keys(STRINGS),
  t(locale, key) {
    const dict = STRINGS[locale] || STRINGS.en;
    return dict[key] ?? STRINGS.en[key] ?? key;
  },
};
