import unittest,json,xml.etree.ElementTree as ET
from demo_data import seeded,value,PREFIX
class DemoDataTest(unittest.TestCase):
    def base(self):
        return ET.fromstring('<map><int name="sessions" value="11"/><boolean name="voiceEnabled" value="false"/><string name="destinationName">기존 목적지</string><string name="destinationLat">37.5</string><string name="destinationLon">127.0</string><string name="garden">{"reflections":[{"goalId":"original","goalText":"기존 목표","afterSessionId":"trip","response":"GOOD","contexts":[],"completedAt":1}],"legacyLeaves":1}</string></map>')
    def test_existing_history_settings_and_input_are_preserved(self):
        base=self.base();data=seeded(base,'arrival',300000000)
        garden=json.loads(value(data,'garden'))
        self.assertEqual('original',garden['reflections'][0]['goalId'])
        self.assertEqual(3,len(garden['reflections']));self.assertEqual(1,garden['legacyLeaves'])
        self.assertEqual('false',value(data,'voiceEnabled'));self.assertEqual('기존 목적지',value(data,'destinationName'))
        self.assertEqual('11',value(base,'sessions'))
    def test_profile_gates_leaf_reward_on_new_session(self):
        for profile,due in [('arrival',True),('departure',False)]:
            data=seeded(self.base(),profile,300000000);g=json.loads(value(data,'garden'))
            self.assertEqual(due,int(value(data,'sessions'))>g['goal']['baseSession'])
            self.assertTrue(g['goal']['id'].startswith(PREFIX))
            self.assertEqual(2,len(json.loads(value(data,'lastMoments'))))
            self.assertEqual('long',next(e for e in data if e.get('name')=='lastGapMs').tag)
            if profile=='departure':self.assertEqual('시연 · 42dot',value(data,'destinationName'))
if __name__=='__main__':unittest.main()
