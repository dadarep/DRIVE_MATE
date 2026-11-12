import unittest
from console_gps_sim import next_progress,point_at,distance
class SimulationTest(unittest.TestCase):
    def test_speed_gear_and_inactive_console(self):
        self.assertEqual(20,next_progress(10,36,'Drive',1,100,True))
        self.assertEqual(10,next_progress(10,36,'Park',1,100,True))
        self.assertEqual(10,next_progress(10,36,'Drive',1,100,False))
        self.assertEqual(0,next_progress(10,36,'Reverse',1,100,True))
        self.assertEqual(100,next_progress(95,36,'Drive',1,100,True))
    def test_geometry_and_route_end(self):
        points=[(127,37),(127.001,37)];length=distance(*points)
        self.assertAlmostEqual(127.0005,point_at(points,[0,length],length/2)[0])
        self.assertEqual(points[-1],point_at(points,[0,length],length*2))
if __name__=='__main__':unittest.main()
